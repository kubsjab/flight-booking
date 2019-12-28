package pl.edu.pw.ii.sag.flightbooking.core.broker

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class BrokerData(brokerId: String, name: String, airlineIds: Set[String])

object Broker {
  // command
  sealed trait Command extends CborSerializable
  final case class BookFlight(airlineId: String, flightId: String, seatId: String, customer: Customer, requestedDate: ZonedDateTime, replyTo: ActorRef[BookingOperationResult]) extends Command
  final case class CancelFlightBooking(airlineId: String, flightId: String, bookingId: String, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetAirlineFlights(replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command
  final case class GetAirlineFlightsBySource(source: String, replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command
  final case class GetAirlineFlightsBySourceAndDestination(source: String, destination: String, replyTo: ActorRef[AirlineFlightDetailsCollection]) extends Command

  // event
  sealed trait Event extends CborSerializable

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class Accepted() extends OperationResult
  final case class Rejected(reason: String) extends OperationResult

  sealed trait BookingOperationResult extends CommandReply
  final case class BookingAccepted(bookingId: String) extends BookingOperationResult
  final case class BookingRejected(reason: String) extends BookingOperationResult
  final case class AirlineFlightDetailsCollection(airlineFlights: Map[String, Seq[FlightDetails]]) extends CommandReply

  //state
  final case class State(airlineActors: Map[String, ActorRef[Airline.Command]]) extends CborSerializable

  def buildId(customId: String): String = s"broker-$customId"

  def apply(brokerData: BrokerData): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(brokerData.brokerId),
        emptyState = State(Map.empty),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }
  }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: BookFlight => bookFlight(context, state, c)
        case c: CancelFlightBooking => cancelFlightBooking(context, state, c)
        case c: GetAirlineFlights => getAirlineFlights(context, state, c)
        case c: GetAirlineFlightsBySource => getAirlineFlightsBySource(context, state, c)
        case c: GetAirlineFlightsBySourceAndDestination => getAirlineFlightsBySourceAndDestination(context, state, c)
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case _ => state
    }
  }

  private def bookFlight(context: ActorContext[Command], state: State, cmd: BookFlight): Effect[Event, State] = {
    state.airlineActors.get(cmd.airlineId) match {
      case Some(airline) =>
        context.spawnAnonymous(FlightBooking.bookFlight(airline, cmd.flightId, cmd.seatId, cmd.customer, cmd.requestedDate, cmd.replyTo))
      case None =>
        cmd.replyTo ! BookingRejected(s"Unable to find airline with id: [${cmd.airlineId}]")
    }
    Effect.none
  }

  private def cancelFlightBooking(context: ActorContext[Command], state: State, cmd: CancelFlightBooking): Effect[Event, State] = {
    state.airlineActors.get(cmd.airlineId) match {
      case Some(airline) =>
        context.spawnAnonymous(FlightBooking.cancelFlightBooking(airline, cmd.flightId, cmd.bookingId, cmd.replyTo))
      case None =>
        cmd.replyTo ! Rejected(s"Unable to find airline with id: [${cmd.airlineId}]")
    }
    Effect.none
  }

  private def getAirlineFlights(context: ActorContext[Command], state: State, cmd: GetAirlineFlights): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlights(airlineActors, cmd.replyTo))
    Effect.none
  }

  private def getAirlineFlightsBySource(context: ActorContext[Command], state: State, cmd: GetAirlineFlightsBySource): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlightsBySource(cmd.source, airlineActors, cmd.replyTo))
    Effect.none
  }

  private def getAirlineFlightsBySourceAndDestination(context: ActorContext[Command], state: State, cmd: GetAirlineFlightsBySourceAndDestination): Effect[Event, State] = {
    val airlineActors = state.airlineActors.values.toSeq
    context.spawnAnonymous(AirlineFlightsQuery.getFlightsBySourceAndDestination(cmd.source, cmd.destination, airlineActors, cmd.replyTo))
    Effect.none
  }

}

