package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration
import scala.concurrent.duration.{FiniteDuration, _}
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

  // event
  sealed trait Event extends CborSerializable
  final case class ClientCreated(clientData: ClientData, clientActor: ActorRef[Client.Command]) extends Event

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
      Behaviors.supervise(
        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(TAG),
          emptyState = State(Map.empty),
          commandHandler = commandHandler(context),
          eventHandler = eventHandler(context))
          .withTagger(taggingAdapter)
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(minBackoff = 2.seconds, maxBackoff = 30.seconds, randomFactor = 0.1))
      ).onFailure[Exception](SupervisorStrategy.restart)
    }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateClient => createClient(context, state, c)
        case c: GetClient => getClient(state, c)
        case c: GetClients => getClients(state, c)
        case c: InitClientsReservationScheduler => initClientsReservationScheduler(state, c)
        case c: InitClientsReservationCancellingScheduler => initClientsReservationCancellingScheduler(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case ClientCreated(clientData, client) =>
        context.log.info(s"Client: [${clientData.clientId}] has been created")
        State(state.clientActors.updated(clientData.clientId, client))
    }
  }

  private def createClient(context: ActorContext[Command], state: State, cmd: CreateClient): ReplyEffect[Event, State] = {
    state.clientActors.get(cmd.clientData.clientId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Client - [${cmd.clientData.clientId}] already exists"))
      case None =>
        val client = context.spawn(Client(cmd.clientData, cmd.brokers), cmd.clientData.clientId)
        client ! Client.StartTicketReservation()
        Effect
          .persist(ClientCreated(cmd.clientData, client))
          .thenReply(cmd.replyTo)(_ => ClientCreationConfirmed(cmd.clientData.clientId))
    }
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