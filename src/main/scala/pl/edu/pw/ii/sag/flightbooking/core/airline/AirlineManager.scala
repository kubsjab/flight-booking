package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable


object AirlineManager {

  final val TAG = "manager-airline"

  // command
  sealed trait Command extends CborSerializable
  final case class CreateAirline(airlineData: AirlineData, replyTo: ActorRef[OperationResult]) extends Command
  final case class GetAirline(airlineId: String, replyTo: ActorRef[AirlineCollection]) extends Command
  final case class GetAirlines(replyTo: ActorRef[AirlineCollection]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class AirlineCreated(airlineData: AirlineData, airlineActor: ActorRef[Airline.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class AirlineCreationConfirmed(airlineId: String) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]]) extends CommandReply

  //state
  final case class State(airlineActors: Map[String, ActorRef[Airline.Command]]) extends CborSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.supervise(
        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(TAG),
          emptyState = State(Map.empty),
          commandHandler = commandHandler(context),
          eventHandler = eventHandler(context))
          .withTagger(taggingAdapter)
      ).onFailure[Exception](SupervisorStrategy.restart)
    }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateAirline => createAirline(context, state, c)
        case c: GetAirline => getAirline(state, c)
        case c: GetAirlines => getAirlines(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case AirlineCreated(airlineData, airline) =>
        context.log.info(s"Airline: [${airlineData.airlineId}] has been created")
        State(state.airlineActors.updated(airlineData.airlineId, airline))
    }
  }

  private def createAirline(context: ActorContext[Command], state: State, cmd: CreateAirline): ReplyEffect[Event, State] = {
    state.airlineActors.get(cmd.airlineData.airlineId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Airline - [${cmd.airlineData.airlineId}] already exists"))
      case None =>
        val airline = context.spawn(Airline(cmd.airlineData), cmd.airlineData.airlineId)
        Effect
          .persist(AirlineCreated(cmd.airlineData, airline))
          .thenReply(cmd.replyTo)(_ => AirlineCreationConfirmed(cmd.airlineData.airlineId))
    }
  }

  private def getAirline(state: State, cmd: GetAirline): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(AirlineCollection(state.airlineActors.view.filterKeys(_ == cmd.airlineId).toMap))
  }

  private def getAirlines(state: State, cmd: GetAirlines): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(AirlineCollection(state.airlineActors))
  }

}