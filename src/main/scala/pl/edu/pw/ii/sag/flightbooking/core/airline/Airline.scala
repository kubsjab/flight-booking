package pl.edu.pw.ii.sag.flightbooking.core.airline

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyStrategyType.ReplyStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.{Flight, FlightDetails, FlightInfo}
import pl.edu.pw.ii.sag.flightbooking.core.airline.query.FlightQuery
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration._

case class AirlineData(airlineId: String, name: String)

case class FlightActorWrapper(flightInfo: FlightInfo, flightActor: ActorRef[Flight.Command])


object Airline {

  final val TAG = "airline"

  // command
  sealed trait Command extends CborSerializable

  final case class CreateFlight(flightInfo: FlightInfo, flightBookingStrategy: FlightBookingStrategyType, replyStrategy: ReplyStrategyType, replyTo: ActorRef[OperationResult]) extends Command

  final case class GetFlights(replyTo: ActorRef[FlightDetailsCollection]) extends Command
  final case class GetFlightsBySource(source: String, replyTo: ActorRef[FlightDetailsCollection]) extends Command
  final case class GetFlightsBySourceAndDestination(source: String, destination: String, replyTo: ActorRef[FlightDetailsCollection]) extends Command
  final case class BookFlight(flightId: String, seatId: String, customer: Customer, requestedDate: ZonedDateTime, replyTo: ActorRef[Flight.BookingOperationResult], requestId: Int) extends Command
  final case class CancelFlightBooking(flightId: String, bookingId: String, replyTo: ActorRef[Flight.CancelBookingOperationResult], requestId: Int) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class FlightCreated(flightInfo: FlightInfo, flightActor: ActorRef[Flight.Command], createdDate: ZonedDateTime) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class FlightCreationConfirmed(flightId: String) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class FlightDetailsCollection(flights: Seq[FlightDetails]) extends CommandReply

  //state
  final case class State(airlineId: String, flightActors: Map[String, FlightActorWrapper]) extends CborSerializable

  def buildId(customId: String): String = s"$TAG-$customId"

  def apply(airlineData: AirlineData): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.supervise(
        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(airlineData.airlineId),
          emptyState = State(airlineData.airlineId, Map.empty),
          commandHandler = commandHandler(context),
          eventHandler = eventHandler(context))
          .withTagger(taggingAdapter)
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(minBackoff = 2.seconds, maxBackoff = 30.seconds, randomFactor = 0.1))
      ).onFailure[Exception](SupervisorStrategy.restart)
    }
  }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateFlight => createFlight(context, state, c)
        case c: GetFlights => getFlights(context, state, c)
        case c: GetFlightsBySource =>  getFlightsBySource(context, state, c)
        case c: GetFlightsBySourceAndDestination =>  getFlightsBySourceAndDestination(context, state, c)
        case c: BookFlight => bookFlight(state, c)
        case c: CancelFlightBooking => cancelFlightBooking(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case FlightCreated(flightInfo, flight, _) =>
        context.log.info(s"Flight: [${flightInfo.flightId}] has been created")
        State(state.airlineId, state.flightActors.updated(flightInfo.flightId, FlightActorWrapper(flightInfo, flight)))
    }
  }

  private def createFlight(context: ActorContext[Command], state: State, cmd: CreateFlight): ReplyEffect[Event, State] = {
    val providedFlightInfo = cmd.flightInfo
    state.flightActors.get(providedFlightInfo.flightId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Flight - [${providedFlightInfo.flightId}] already exists"))
      case None =>
        val flightInfo = FlightInfo(
          providedFlightInfo.flightId,
          state.airlineId,
          providedFlightInfo.plane,
          providedFlightInfo.startDatetime,
          providedFlightInfo.endDatetime,
          providedFlightInfo.source,
          providedFlightInfo.destination)

        val flight = context.spawn(Flight(flightInfo, cmd.flightBookingStrategy, cmd.replyStrategy), flightInfo.flightId)

        Effect
          .persist(FlightCreated(flightInfo, flight, ZonedDateTime.now()))
          .thenReply(cmd.replyTo)(_ => FlightCreationConfirmed(flightInfo.flightId))
    }
  }

  private def getFlights(context: ActorContext[Command], state: State, cmd: GetFlights): Effect[Event, State] = {
    val flightActors = state.flightActors
      .map(_._2.flightActor)
      .toSeq

    getFlights(context, flightActors, cmd.replyTo)
  }

  private def getFlightsBySource(context: ActorContext[Command],
                                 state: State,
                                 cmd: GetFlightsBySource): Effect[Event, State] = {
    val flightActors = state.flightActors
      .filter(_._2.flightInfo.source == cmd.source)
      .map(_._2.flightActor)
      .toSeq

    getFlights(context, flightActors, cmd.replyTo)
  }

  private def getFlightsBySourceAndDestination(context: ActorContext[Command],
                                               state: State,
                                               cmd: GetFlightsBySourceAndDestination): Effect[Event, State] = {
    val flightActors = state.flightActors
      .filter(f => f._2.flightInfo.source == cmd.source && f._2.flightInfo.destination == cmd.destination)
      .map(_._2.flightActor)
      .toSeq

    getFlights(context, flightActors, cmd.replyTo)
  }

  private def getFlights(context: ActorContext[Command],
                         flightActors: Seq[ActorRef[Flight.Command]],
                         replyTo: ActorRef[FlightDetailsCollection]): Effect[Event, State] = {
    context.spawnAnonymous(FlightQuery(flightActors, replyTo))
    Effect.none
  }

  private def bookFlight(state: State, cmd: BookFlight): Effect[Event, State] = {
    state.flightActors.get(cmd.flightId) match {
      case Some(flightActorWrapper) => flightActorWrapper.flightActor ! Flight.Book(cmd.flightId, cmd.seatId, cmd.customer, cmd.replyTo, cmd.requestId)
      case None => cmd.replyTo ! Flight.BookingRejected(s"Unable to find flight with id: [${cmd.flightId}]", cmd.requestId)
    }
    Effect.none
  }

  private def cancelFlightBooking(state: State, cmd: CancelFlightBooking): Effect[Event, State] = {
    state.flightActors.get(cmd.flightId) match {
      case Some(flightActorWrapper) => flightActorWrapper.flightActor ! Flight.CancelBooking(cmd.flightId, cmd.bookingId, cmd.replyTo, cmd.requestId)
      case None => cmd.replyTo ! Flight.CancelBookingRejected(s"Unable to find flight with id: [${cmd.flightId}]", cmd.requestId)
    }
    Effect.none
  }

}
