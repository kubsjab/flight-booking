package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object ClientManager {

  final val TAG = "manager-client"

  // command
  sealed trait Command extends CborSerializable
  final case class CreateClient(clientData: ClientData, brokers: Map[String, ActorRef[Broker.Command]], replyTo: ActorRef[OperationResult]) extends Command
  final case class GetClient(clientId: String, replyTo: ActorRef[ClientCollection]) extends Command
  final case class GetClients(replyTo: ActorRef[ClientCollection]) extends Command
  final case class InitClientsReservationScheduler(delayMin: Int, delayMax: Int) extends Command
  final case class InitClientsReservationCancellingScheduler(delayMin: Int, delayMax: Int) extends Command
  private final case class TerminateClient(clientId: String, client: ActorRef[Client.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class ClientCreated(ClientData: ClientData, brokers: Map[String, ActorRef[Broker.Command]]) extends Event
  final case class ClientTerminated(clientId: String, client: ActorRef[Client.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class ClientCreationConfirmed(clientId: String) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class ClientCollection(clients: Map[String, ActorRef[Client.Command]]) extends CommandReply

  //state
  final case class State(clientActors: Map[String, ActorRef[Client.Command]]) extends CborSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(TAG),
        emptyState = State(Map.empty),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
        .withTagger(taggingAdapter)

    }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateClient => createClient(context, state, c)
        case c: TerminateClient => terminateClient(context, state, c)
        case c: GetClient => getClient(state, c)
        case c: GetClients => getClients(state, c)
        case c: InitClientsReservationScheduler => initClientsReservationScheduler(state, c)
        case c: InitClientsReservationCancellingScheduler => initClientsReservationCancellingScheduler(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case ClientCreated(clientData, brokers) =>
        val client = context.spawn(Client(clientData, brokers), clientData.clientId)
        context.watchWith(client, TerminateClient(clientData.clientId, client))
        context.log.info(s"Client: [${clientData.clientId}] has been created")
        client ! Client.StartTicketReservation()
        State(state.clientActors.updated(clientData.clientId, client))
      case ClientTerminated(clientId, client) =>
        context.log.info(s"Client: [${clientId}] has been terminated")
        context.unwatch(client)
        State(state.clientActors - clientId)
    }
  }

  private def createClient(context: ActorContext[Command], state: State, cmd: CreateClient): ReplyEffect[Event, State] = {
    state.clientActors.get(cmd.clientData.clientId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Client - [${cmd.clientData.clientId}] already exists"))
      case None =>
        Effect
          .persist(ClientCreated(cmd.clientData, cmd.brokers))
          .thenReply(cmd.replyTo)(_ => ClientCreationConfirmed(cmd.clientData.clientId))
    }
  }

  private def terminateClient(context: ActorContext[Command], state: State, cmd: TerminateClient): Effect[Event, State] = {
    Effect.persist(ClientTerminated(cmd.clientId, cmd.client))
  }

  private def getClient(state: State, cmd: GetClient): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(ClientCollection(state.clientActors.view.filterKeys(_ == cmd.clientId).toMap))
  }

  private def getClients(state: State, cmd: GetClients): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(ClientCollection(state.clientActors))
  }

  private def initClientsReservationScheduler(state: State, cmd: InitClientsReservationScheduler): Effect[Event, State] = {
    state.clientActors.foreach(
      clientInfo => clientInfo._2 ! Client.InitScheduledTicketReservation(Client.StartTicketReservation(), FiniteDuration(Random.between(cmd.delayMin, cmd.delayMax), duration.SECONDS)))
    Effect.none
  }

  private def initClientsReservationCancellingScheduler(state: State, cmd: InitClientsReservationCancellingScheduler): Effect[Event, State] = {
    state.clientActors.foreach(
      clientInfo => clientInfo._2 ! Client.InitScheduledReservationCancelling(Client.StartTicketCancelling(), FiniteDuration(Random.between(cmd.delayMin, cmd.delayMax), duration.SECONDS)))
    Effect.none
  }
}