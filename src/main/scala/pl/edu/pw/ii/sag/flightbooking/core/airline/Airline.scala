package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.{Flight, FlightData, FlightDetailsWrapper}
import pl.edu.pw.ii.sag.flightbooking.core.airline.query.FlightQuery
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class AirlineData(airlineId: String, name: String)

case class FlightActorWrapper(flightData: FlightData, flightActor: ActorRef[Flight.Command])


object Airline {

  // command
  sealed trait Command extends CborSerializable
  final case class CreateFlight(flightData: FlightData, flightBookingStrategy: FlightBookingStrategyType, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetFlights(replyTo: ActorRef[FlightDetailsCollection]) extends Command
  final case class GetFlightsBySource(source: String, replyTo: ActorRef[FlightDetailsCollection]) extends Command
  final case class GetFlightsBySourceAndDestination(source: String, destination: String, replyTo: ActorRef[FlightDetailsCollection]) extends Command
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
  final case class FlightDetailsCollection(airlines: Seq[FlightDetailsWrapper]) extends CommandReply

  //state
  final case class State(flightActors: Map[String, FlightActorWrapper]) extends CborSerializable

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
        case c: GetFlights => getFlights(context, state, c)
        case c: GetFlightsBySource =>  getFlightsBySource(context, state, c)
        case c: GetFlightsBySourceAndDestination =>  getFlightsBySourceAndDestination(context, state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case FlightCreated(flightData, flightBookingStrategy) =>
        val flight = context.spawn(Flight(flightData,flightBookingStrategy), flightData.flightId)
        context.watchWith(flight, TerminateFlight(flightData.flightId, flight))
        context.log.info(s"Flight: [${flightData.flightId}] has been created")
        State(state.flightActors.updated(flightData.flightId, FlightActorWrapper(flightData, flight)))
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
      .filter(_._2.flightData.source == cmd.source)
      .map(_._2.flightActor)
      .toSeq

    getFlights(context, flightActors, cmd.replyTo)
  }

  private def getFlightsBySourceAndDestination(context: ActorContext[Command],
                                               state: State,
                                               cmd: GetFlightsBySourceAndDestination): Effect[Event, State] = {
    val flightActors = state.flightActors
      .filter(f => f._2.flightData.source == cmd.source && f._2.flightData.destination == cmd.destination)
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
}
