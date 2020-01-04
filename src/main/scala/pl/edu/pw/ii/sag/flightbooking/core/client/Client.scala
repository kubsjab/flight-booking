package pl.edu.pw.ii.sag.flightbooking.core.client

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.core.client.booking.BookingData
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class ClientData(clientId: String, name: String, brokerIds: Set[String])

object Client {

  // command
  sealed trait Command extends CborSerializable
  private final case class RemoveBroker(brokerId: String, broker: ActorRef[Broker.Command]) extends Command

  final case class Start() extends Command

  final case class StartTicketReservation() extends Command

  private final case class WrappedBookingOperationResult(response: Broker.BookingOperationResult) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class BrokerTerminated(brokerId: String, broker: ActorRef[Broker.Command]) extends Event

  private final case class TickedReservationStarted(bookingData: BookingData) extends Event

  private final case class BookingAccepted(requestId: Int, bookingId: String) extends Event

  private final case class BookingRejected(requestId: Int, reason: String) extends Event


  // reply
  sealed trait CommandReply extends CborSerializable

  sealed trait OperationResult extends CommandReply

  final case class Accepted() extends OperationResult

  final case class Rejected(reason: String) extends OperationResult

  sealed trait BookingOperationResult extends CommandReply

  //state
  final case class State(
                          brokerActors: Map[String, ActorRef[Broker.Command]],
                          clientData: ClientData,
                          bookingRequests: Map[Int, BookingData],
                          nextRequestId: Int
                        ) extends CborSerializable {

    def applyEvent(event: Event): State = {
      event match {
        case TickedReservationStarted(bookingData) =>
          copy(brokerActors, clientData, bookingRequests.updated(bookingData.id, bookingData), nextRequestId + 1)
        case BookingAccepted(requestId, bookingId) =>
          val updatedBookingRequests = bookingRequests.get(requestId) match {
            case Some(bookingData) => bookingRequests + (requestId -> bookingData.accepted(bookingId))
          }
          copy(brokerActors, clientData, updatedBookingRequests, nextRequestId)
        case BookingRejected(requestId, reason) =>
          val updatedBookingRequests = bookingRequests.get(requestId) match {
            case Some(bookingData) => bookingRequests + (requestId -> bookingData.rejected(reason))
          }
          copy(brokerActors, clientData, updatedBookingRequests, nextRequestId)
      }
    }
  }


  def buildId(customId: String): String = s"client-$customId"

  def getInitialState(brokerActors: Map[String, ActorRef[Broker.Command]], clientData: ClientData): State = {
    State(brokerActors, clientData, Map.empty, 0)
  }

  def apply(clientData: ClientData, brokers:Map[String, ActorRef[Broker.Command]]): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(clientData.clientId),
        emptyState = getInitialState(brokers, clientData),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }
  }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case WrappedBookingOperationResult(response) => handleBookingResponse(context, response): Effect[Event, State]
        case Start() => startClient(context, state)
        case c: RemoveBroker => brokerTerminated(context, state, c)
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BrokerTerminated(brokerId, broker) =>
        context.log.info(s"Broker: [${brokerId}] has been terminated. Removing from Client.")
        context.unwatch(broker)
        State(state.brokerActors - brokerId)
      case _ => state.applyEvent(event)
    }
  }

  def handleBookingResponse(context: ActorContext[Command], response: Broker.BookingOperationResult): Effect[Event, State] = {
    response match {
      case Broker.BookingAccepted(bookingId, requestId) =>
        context.log.info(s"Reservation completed succesfully, id : [$bookingId]")
        Effect.persist(BookingAccepted(requestId, bookingId))
      case Broker.BookingRejected(reason, requestId) =>
        context.log.info(s"Reservation rejected, reason: $reason")
        Effect.persist(BookingRejected(requestId, reason))
      case _ => Effect.none
    }
  }

  def startClient(context: ActorContext[Command], state: State): Effect[Event, State] = {
    val broker = state.brokerActors.head._2
    var details: FlightDetails = null
    bookTicket(context, state, broker, details)
  }

  private def bookTicket(context: ActorContext[Command], state: State, broker: ActorRef[Broker.Command], details: FlightDetails): Effect[Event, State] = {

    val brokerBookingOperationResponseWrapper: ActorRef[Broker.BookingOperationResult] = context.messageAdapter(rsp => WrappedBookingOperationResult(rsp))

    val data = BookingData(state.nextRequestId, "", details.flightInfo, getAvaliableSeat(details.seatReservations))

    Effect.persist(TickedReservationStarted(data))
      .thenRun(state =>
        broker ! Broker.BookFlight(data.flightInfo.airlineId,
          data.flightInfo.flightId,
          data.seat,
          Customer(state.clientData.name,
            state.clientData.name),
          ZonedDateTime.now(),
          brokerBookingOperationResponseWrapper,
          data.id
        ))
  }

  private def getAvaliableSeat(seats: Map[String, Boolean]) = {
    seats
      .toList
      .filter(_._2 == false)
      .head
      ._1
  }

  private def brokerTerminated(contxt: ActorContext[Command], state: State, cmd: RemoveBroker): Effect[Event, State] ={
    Effect.persist(BrokerTerminated(cmd.brokerId, cmd.broker))
  }
}
