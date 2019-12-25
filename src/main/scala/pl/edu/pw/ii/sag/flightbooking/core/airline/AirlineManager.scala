package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable


object AirlineManager {

  // command
  sealed trait Command extends CborSerializable
  final case class CreateAirline(airlineData: AirlineData, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetAirlines(replyTo: ActorRef[AirlineCollection]) extends Command
  private final case class TerminateAirline(airlineId: String, airline: ActorRef[Airline.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class AirlineCreated(airlineData: AirlineData) extends Event
  final case class AirlineTerminated(airlineId: String, airline: ActorRef[Airline.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class AirlineCreationConfirmed(airlineId: String, airline: ActorRef[Airline.Command]) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]]) extends CommandReply

  //state
  final case class State(airlineActors: Map[String, ActorRef[Airline.Command]]) extends CborSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId("airline-manager"),
        emptyState = State(Map.empty),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateAirline => createAirline(context, state, c)
        case c: TerminateAirline => terminateAirline(context, state, c)
        case c: GetAirlines => getAirlines(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case AirlineCreated(airlineData) =>
        val airline = context.spawn(Airline(airlineData), airlineData.airlineId)
        context.watchWith(airline, TerminateAirline(airlineData.airlineId, airline))
        context.log.info(s"Airline: [${airlineData.airlineId}] has been created")
        State(state.airlineActors.updated(airlineData.airlineId, airline))
      case AirlineTerminated(airlineId, airline) =>
        context.log.info(s"Airline: [${airlineId}] has been terminated")
        context.unwatch(airline)
        State(state.airlineActors - airlineId)
    }
  }

  private def createAirline(context: ActorContext[Command], state: State, cmd: CreateAirline): ReplyEffect[Event, State] = {
    state.airlineActors.get(cmd.airlineData.airlineId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Airline - [${cmd.airlineData.airlineId}] already exists"))
      case None =>
        Effect
          .persist(AirlineCreated(cmd.airlineData))
          .thenReply(cmd.replyTo)(_ => AirlineCreationConfirmed(cmd.airlineData.airlineId))
    }
  }

  private def terminateAirline(context: ActorContext[Command], state: State, cmd: TerminateAirline): Effect[Event, State] = {
    Effect.persist(AirlineTerminated(cmd.airlineId, cmd.airline))
  }

  private def getAirlines(state: State, cmd: GetAirlines): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(AirlineCollection(state.airlineActors))
  }

}