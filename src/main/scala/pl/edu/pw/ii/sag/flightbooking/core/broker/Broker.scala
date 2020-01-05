package pl.edu.pw.ii.sag.flightbooking.core.broker

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class BrokerData(brokerId: String, name: String, airlineIds: Set[String])

object Broker {

  final val TAG = "broker"

  // command
  sealed trait Command extends CborSerializable
  final case class BookFlight(airlineId: String, flightId: String, seatId: String, customer: Customer, requestedDate: ZonedDateTime, replyTo: ActorRef[BookingOperationResult], requestId: Int) extends Command
  final case class CancelFlightBooking(airlineId: String, flightId: String, bookingId: String, replyTo: ActorRef[CancelBookingOperationResult]) extends Command
  final case class GetAirlineFlights(replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command
  final case class GetAirlineFlightsBySource(source: String, replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command
  final case class GetAirlineFlightsBySourceAndDestination(source: String, destination: String, replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command
  private final case class RemoveAirline(airlineId: String, airline: ActorRef[Airline.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class AirlineTerminated(airlineId: String, airline: ActorRef[Airline.Command]) extends Event

  //state
  final case class State(brokerId: String, airlineActors: Map[String, ActorRef[Airline.Command]]) extends CborSerializable

  // reply
  sealed trait CommandReply extends CborSerializable

  sealed trait BookingOperationResult extends CommandReply

  final case class BookingAccepted(bookingId: String, requestId: Int) extends BookingOperationResult
  sealed trait BookingFailed extends BookingOperationResult

  final case class BookingRejected(reason: String, requestId: Int) extends BookingFailed

  sealed trait CancelBookingOperationResult extends CommandReply
  final case class CancelBookingAccepted() extends CancelBookingOperationResult
  sealed trait CancelBookingFailed extends CancelBookingOperationResult
  final case class CancelBookingRejected(reason: String) extends CancelBookingFailed

  sealed trait SystemFailure extends CommandReply with BookingOperationResult with CancelBookingOperationResult
  final case class Timeout() extends SystemFailure
  final case class GeneralSystemFailure(reason: String) extends SystemFailure

  final case class AirlineFlightDetailsCollection(airlineFlights: Map[String, Seq[FlightDetails]], brokerId: String) extends CommandReply


  def buildId(customId: String): String = s"$TAG-$customId"

  def apply(brokerData: BrokerData, airlines: Map[String, ActorRef[Airline.Command]]): Behavior[Command] = {
    Behaviors.setup { context =>
      airlines.foreach(airlineInfo => context.watchWith(airlineInfo._2, RemoveAirline(airlineInfo._1, airlineInfo._2)))
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(brokerData.brokerId),
        emptyState = State(brokerData.brokerId, airlines),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
        .withTagger(taggingAdapter)

    }
  }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)


  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: BookFlight => bookFlight(context, state, c)
        case c: CancelFlightBooking => cancelFlightBooking(context, state, c)
        case c: GetAirlineFlights => getAirlineFlights(context, state, c)
        case c: GetAirlineFlightsBySource => getAirlineFlightsBySource(context, state, c)
        case c: GetAirlineFlightsBySourceAndDestination => getAirlineFlightsBySourceAndDestination(context, state, c)
        case c: RemoveAirline => airlineTerminated(c)
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case AirlineTerminated(airlineId, airline) =>
        context.log.info(s"Airline: [${airlineId}] has been terminated. Removing from Broker.")
        context.unwatch(airline)
        state.copy(airlineActors = state.airlineActors - airlineId)
      case _ => state
    }
  }

  private def airlineTerminated(cmd: RemoveAirline): Effect[Event, State] ={
    Effect.persist(AirlineTerminated(cmd.airlineId, cmd.airline))
  }

  private def bookFlight(context: ActorContext[Command], state: State, cmd: BookFlight): Effect[Event, State] = {
    state.airlineActors.get(cmd.airlineId) match {
      case Some(airline) =>
        context.spawnAnonymous(FlightBooking.bookFlight(airline, cmd.flightId, cmd.seatId, cmd.customer, cmd.requestedDate, cmd.replyTo, cmd.requestId))
      case None =>
        cmd.replyTo ! GeneralSystemFailure(s"Unable to find airline with id: [${cmd.airlineId}]")
    }
    Effect.none
  }

  private def cancelFlightBooking(context: ActorContext[Command], state: State, cmd: CancelFlightBooking): Effect[Event, State] = {
    state.airlineActors.get(cmd.airlineId) match {
      case Some(airline) =>
        context.spawnAnonymous(FlightBooking.cancelFlightBooking(airline, cmd.flightId, cmd.bookingId, cmd.replyTo))
      case None =>
        cmd.replyTo ! GeneralSystemFailure(s"Unable to find airline with id: [${cmd.airlineId}]")
    }
    Effect.none
  }

  private def getAirlineFlights(context: ActorContext[Command], state: State, cmd: GetAirlineFlights): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlights(airlineActors, state.brokerId, cmd.replyTo))
    Effect.none
  }

  private def getAirlineFlightsBySource(context: ActorContext[Command], state: State, cmd: GetAirlineFlightsBySource): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlightsBySource(cmd.source, airlineActors, state.brokerId, cmd.replyTo))
    Effect.none
  }

  private def getAirlineFlightsBySourceAndDestination(context: ActorContext[Command], state: State, cmd: GetAirlineFlightsBySourceAndDestination): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlightsBySourceAndDestination(cmd.source, cmd.destination, airlineActors, state.brokerId, cmd.replyTo))
    Effect.none
  }

}

