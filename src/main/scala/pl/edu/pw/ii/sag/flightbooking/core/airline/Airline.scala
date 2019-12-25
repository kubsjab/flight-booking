package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.{Flight, FlightData}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class AirlineData(airlineId: String, name: String)


object Airline {

  // command
  sealed trait Command extends CborSerializable
  final case class CreateFlight(flightData: FlightData, flightBookingStrategy: FlightBookingStrategyType, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetFlights(replyTo: ActorRef[FlightCollection]) extends Command
  private final case class TerminateFlight(flightId: String, flight: ActorRef[Flight.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class FlightCreated(flightData: FlightData, flightBookingStrategy: FlightBookingStrategyType) extends Event
  final case class FlightTerminated(flightId: String, flight: ActorRef[Flight.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class FlightCreationConfirmed(flightId: String) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class FlightCollection(airlines: Map[String, ActorRef[Flight.Command]]) extends CommandReply

  //state
  final case class State(flightActors: Map[String, ActorRef[Flight.Command]]) extends CborSerializable

  def buildId(customId: String): String = s"airline-$customId"

  def apply(airlineData: AirlineData): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(airlineData.airlineId),
        emptyState = State(Map.empty),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }
  }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateFlight => createFlight(context, state, c)
        case c: TerminateFlight => terminateFlight(context, state, c)
        case c: GetFlights => getFlights(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case FlightCreated(flightData, flightBookingStrategy) =>
        val flight = context.spawn(Flight(flightData,flightBookingStrategy), flightData.flightId)
        context.watchWith(flight, TerminateFlight(flightData.flightId, flight))
        context.log.debug(s"Flight: [${flightData.flightId}] has been created")
        State(state.flightActors.updated(flightData.flightId, flight))
      case FlightTerminated(flightId, flight) =>
        context.log.info(s"Flight: [${flightId}] has been terminated")
        context.unwatch(flight)
        State(state.flightActors - flightId)
    }
  }

  private def createFlight(context: ActorContext[Command], state: State, cmd: CreateFlight): ReplyEffect[Event, State] = {
    state.flightActors.get(cmd.flightData.flightId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Flight - [${cmd.flightData.flightId}] already exists"))
      case None =>
        Effect
          .persist(FlightCreated(cmd.flightData, cmd.flightBookingStrategy))
          .thenReply(cmd.replyTo)(_ => FlightCreationConfirmed(cmd.flightData.flightId))
    }
  }

  private def terminateFlight(context: ActorContext[Command], state: State, cmd: TerminateFlight): Effect[Event, State] = {
    Effect.persist(FlightTerminated(cmd.flightId, cmd.flight))
  }

  private def getFlights(state: State, cmd: GetFlights): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(FlightCollection(state.flightActors))
  }
}
