package pl.edu.pw.ii.sag.flightbooking.core.broker

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable


object BrokerManager {
  // command
  sealed trait Command extends CborSerializable
  final case class CreateBroker(brokerData: BrokerData, airlines: Map[String, ActorRef[Airline.Command]], replyTo: ActorRef[OperationResult]) extends Command
  final case class GetBroker(brokerId: String, replyTo: ActorRef[BrokerCollection]) extends Command
  final case class GetBrokers(replyTo: ActorRef[BrokerCollection]) extends Command
  private final case class TerminateBroker(brokerId: String, broker: ActorRef[Broker.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class BrokerCreated(BrokerData: BrokerData, airlines: Map[String, ActorRef[Airline.Command]]) extends Event
  final case class BrokerTerminated(brokerId: String, broker: ActorRef[Broker.Command]) extends Event

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
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId("broker-manager"),
        emptyState = State(Map.empty),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: CreateBroker => createBroker(context, state, c)
        case c: TerminateBroker => terminateBroker(context, state, c)
        case c: GetBroker => getBroker(state, c)
        case c: GetBrokers => getBrokers(state, c)
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BrokerCreated(brokerData, airlines) =>
        val broker = context.spawn(Broker(brokerData, airlines), brokerData.brokerId)
        context.watchWith(broker, TerminateBroker(brokerData.brokerId, broker))
        context.log.info(s"Broker: [${brokerData.brokerId}] has been created")
        State(state.brokerActors.updated(brokerData.brokerId, broker))
      case BrokerTerminated(brokerId, broker) =>
        context.log.info(s"Broker: [${brokerId}] has been terminated")
        context.unwatch(broker)
        State(state.brokerActors - brokerId)
    }
  }

  private def createBroker(context: ActorContext[Command], state: State, cmd: CreateBroker): ReplyEffect[Event, State] = {
    state.brokerActors.get(cmd.brokerData.brokerId) match {
      case Some(_) => Effect.reply(cmd.replyTo)(Rejected(s"Broker - [${cmd.brokerData.brokerId}] already exists"))
      case None =>
        Effect
          .persist(BrokerCreated(cmd.brokerData, cmd.airlines))
          .thenReply(cmd.replyTo)(_ => BrokerCreationConfirmed(cmd.brokerData.brokerId))
    }
  }

  private def terminateBroker(context: ActorContext[Command], state: State, cmd: TerminateBroker): Effect[Event, State] = {
    Effect.persist(BrokerTerminated(cmd.brokerId, cmd.broker))
  }

  private def getBroker(state: State, cmd: GetBroker): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(BrokerCollection(state.brokerActors.view.filterKeys(_ == cmd.brokerId).toMap))
  }

  private def getBrokers(state: State, cmd: GetBrokers): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(BrokerCollection(state.brokerActors))
  }

}