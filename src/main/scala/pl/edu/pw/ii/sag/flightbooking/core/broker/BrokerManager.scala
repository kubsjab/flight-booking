package pl.edu.pw.ii.sag.flightbooking.core.broker

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration._


object BrokerManager {

  final val TAG = "manager-broker"

  // command
  sealed trait Command extends CborSerializable
  final case class CreateBroker(brokerData: BrokerData, airlines: Map[String, ActorRef[Airline.Command]], replyTo: ActorRef[OperationResult]) extends Command
  final case class GetBroker(brokerId: String, replyTo: ActorRef[BrokerCollection]) extends Command
  final case class GetBrokers(replyTo: ActorRef[BrokerCollection]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class BrokerCreated(brokerData: BrokerData, brokerActor: ActorRef[Broker.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class BrokerCreationConfirmed(brokerId: String) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class BrokerCollection(brokers: Map[String, ActorRef[Broker.Command]]) extends CommandReply

  //state
  final case class State(brokerActors: Map[String, ActorRef[Broker.Command]]) extends CborSerializable

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
        case c: CreateBroker => createBroker(context, state, c)
        case c: GetBroker => getBroker(state, c)
        case c: GetBrokers => getBrokers(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BrokerCreated(brokerData, broker) =>
        context.log.info(s"Broker: [${brokerData.brokerId}] has been created")
        State(state.brokerActors.updated(brokerData.brokerId, broker))
    }
  }

  private def createBroker(context: ActorContext[Command], state: State, cmd: CreateBroker): ReplyEffect[Event, State] = {
    state.brokerActors.get(cmd.brokerData.brokerId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Broker - [${cmd.brokerData.brokerId}] already exists"))
      case None =>
        val broker = context.spawn(Broker(cmd.brokerData, cmd.airlines), cmd.brokerData.brokerId)
        Effect
          .persist(BrokerCreated(cmd.brokerData, broker))
          .thenReply(cmd.replyTo)(_ => BrokerCreationConfirmed(cmd.brokerData.brokerId))
    }
  }

  private def getBroker(state: State, cmd: GetBroker): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(BrokerCollection(state.brokerActors.view.filterKeys(_ == cmd.brokerId).toMap))
  }

  private def getBrokers(state: State, cmd: GetBrokers): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(BrokerCollection(state.brokerActors))
  }

}