package pl.edu.pw.ii.sag.flightbooking.core.client

import java.time.ZonedDateTime
import java.util.concurrent.TimeoutException

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.core.client.booking.BookingData
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Failure, Random, Success}

case class ClientData(clientId: String, name: String, brokerIds: Set[String])

object Client {

  final val TAG = "client"

  // command
  sealed trait Command extends CborSerializable

  private final case class RemoveBroker(brokerId: String, broker: ActorRef[Broker.Command]) extends Command

  final case class StartTicketReservation() extends Command
  private final case class WrappedBookingOperationResult(response: Broker.BookingOperationResult) extends Command

  private final case class WrappedAggregatedBrokerFlights(response: BrokerFlightsQuery.AggregatedBrokerFlights) extends Command

  private final case class BookingFailedResult(exception: Throwable, requestId: Int) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class BrokerTerminated(brokerId: String, broker: ActorRef[Broker.Command]) extends Event
  private final case class TicketReservationStarted(bookingData: BookingData) extends Event
  private final case class BookingAccepted(requestId: Int, bookingId: String) extends Event

  private final case class BookingRejected(requestId: Int, reason: String) extends Event

  private final case class BookingFailed(requestId: Int, reason: String) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class Accepted() extends OperationResult
  final case class Rejected(reason: String) extends OperationResult

  //state
  final case class State(
                          brokerActors: Map[String, ActorRef[Broker.Command]],
                          clientData: ClientData,
                          bookingRequests: Map[Int, BookingData],
                          nextRequestId: Int
                        ) extends CborSerializable {
  }


  def buildId(customId: String): String = s"$TAG-$customId"

  def apply(clientData: ClientData, brokers: Map[String, ActorRef[Broker.Command]]): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(clientData.clientId),
        emptyState = getInitialState(brokers, clientData),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
        .withTagger(taggingAdapter)

    }
  }

  private def getInitialState(brokerActors: Map[String, ActorRef[Broker.Command]], clientData: ClientData): State = {
    State(brokerActors, clientData, Map.empty, 0)
  }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case StartTicketReservation() => findAvailableFlights(context, state)
        case WrappedBookingOperationResult(response) => handleBookingResponse(context, response): Effect[Event, State]
        case BookingFailedResult(exception, requestId) => handleBookingFailedResult(context, exception, requestId): Effect[Event, State]
        case WrappedAggregatedBrokerFlights(response) => handleGetFlightsQueryResponse(context, state, response): Effect[Event, State]
        case c: RemoveBroker => brokerTerminated(c)
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BrokerTerminated(brokerId, broker) =>
        context.log.info(s"Broker: [${brokerId}] has been terminated. Removing from Client.")
        context.unwatch(broker)
        state.copy(brokerActors = state.brokerActors - brokerId)

      case TicketReservationStarted(bookingData) =>
        state.copy(bookingRequests = state.bookingRequests.updated(bookingData.id, bookingData), nextRequestId = state.nextRequestId + 1)

      case BookingAccepted(requestId, bookingId) =>
        val updatedBookingRequests = state.bookingRequests.get(requestId) match {
          case Some(bookingData) => state.bookingRequests + (requestId -> bookingData.accepted(bookingId))
        }
        state.copy(bookingRequests = updatedBookingRequests)

      case BookingRejected(requestId, reason) =>
        rejectBooking(state, requestId, reason)

      case BookingFailed(requestId, reason) =>
        rejectBooking(state, requestId, reason)
    }
  }

  private def rejectBooking(state: State, requestId: Int, reason: String) = {
    val updatedBookingRequests = state.bookingRequests.get(requestId) match {
      case Some(bookingData) => state.bookingRequests + (requestId -> bookingData.rejected(reason))
    }
    state.copy(bookingRequests = updatedBookingRequests)
  }

  def handleGetFlightsQueryResponse(context: ActorContext[Command], state: State, response: BrokerFlightsQuery.AggregatedBrokerFlights): Effect[Event, State] = {
    val availableFlights = response.brokerFlights.toList.flatMap(c => c._2.map(x => (c._1, x)))

    if (availableFlights.isEmpty) {
      context.log.info(s"No available flights for requested query")
      return Effect.none
    }

    val randomFlight = availableFlights.apply(Random.nextInt(availableFlights.length))
    bookTicket(context, state, randomFlight._1, randomFlight._2)
  }


  def handleBookingResponse(context: ActorContext[Command], response: Broker.BookingOperationResult): Effect[Event, State] = {
    response match {
      case Broker.GeneralSystemFailure(reason, requestId) =>
        context.log.info(s"Reservation failed, broker returned failure")
        Effect.persist(BookingFailed(requestId, reason))
      case Broker.Timeout(requestId) =>
        context.log.info(s"Reservation failed, broker returned internal timeout")
        Effect.persist(BookingFailed(requestId, "Broker returned internal timeout"))
      case Broker.BookingAccepted(bookingId, requestId) =>
        context.log.info(s"Reservation completed succesfully, id : [$bookingId]")
        Effect.persist(BookingAccepted(requestId, bookingId))
      case Broker.BookingRejected(reason, requestId) =>
        context.log.info(s"Reservation rejected, reason: $reason")
        Effect.persist(BookingRejected(requestId, reason))
      case _ => Effect.none
    }
  }

  private def handleBookingFailedResult(context: ActorContext[Command], exception: Throwable, requestId: Int): Effect[Event, State] = {
    exception match {
      case _: TimeoutException =>
        context.log.info(s"Reservation failed, broker timed out")
        Effect.persist(BookingFailed(requestId, "Broker timeout"))
      case _ =>
        context.log.info(s"Reservation failed, exception occured")
        Effect.persist(BookingFailed(requestId, exception.getMessage))
    }
  }


  def findAvailableFlights(context: ActorContext[Command], state: State): Effect[Event, State] = {
    val queryResponseWrapper: ActorRef[BrokerFlightsQuery.AggregatedBrokerFlights] = context.messageAdapter(rsp => WrappedAggregatedBrokerFlights(rsp))
    context.spawnAnonymous(BrokerFlightsQuery.getFlights(state.brokerActors.values.toList, queryResponseWrapper))
    Effect.none
  }

  private def bookTicket(context: ActorContext[Command], state: State, brokerId: String, details: FlightDetails): Effect[Event, State] = {
    val data = BookingData(state.nextRequestId, brokerId, details.flightInfo, getAvailableSeat(details.seatReservations))
    val broker = state.brokerActors(brokerId)
    implicit val timeout: akka.util.Timeout = FiniteDuration(Configuration.Core.Client.bookingTimeout, SECONDS)
    context.log.debug(s"Starting booking reservation from $brokerId for $data")

    Effect.persist(TicketReservationStarted(data))
      .thenRun(state =>
        context.ask(broker, (ref: ActorRef[Broker.BookingOperationResult]) =>
          Broker.BookFlight(data.flightInfo.airlineId,
            data.flightInfo.flightId,
            data.seat,
            Customer(state.clientData.name, state.clientData.name),
            ZonedDateTime.now(),
            ref,
            data.id
          )
        ) {
          case Success(rsp) => WrappedBookingOperationResult(rsp)
          case Failure(ex) => BookingFailedResult(ex, data.id)
        }
      )
  }

  private def getAvailableSeat(seats: Map[String, Boolean]) = {
    val availableSeats =
      seats
        .toList
        .filter(_._2 == false)

    availableSeats.apply(Random.nextInt(availableSeats.length))._1
  }

  private def brokerTerminated(cmd: RemoveBroker): Effect[Event, State] = {
    Effect.persist(BrokerTerminated(cmd.brokerId, cmd.broker))
  }
}
