package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class ClientData(clientId: String, name: String, brokerIds: Set[String])
object Client {

  final val TAG = "client"

  // command
  sealed trait Command extends CborSerializable
  private final case class RemoveBroker(brokerId: String, broker: ActorRef[Broker.Command]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class BrokerTerminated(brokerId: String, broker: ActorRef[Broker.Command]) extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class Accepted() extends OperationResult
  final case class Rejected(reason: String) extends OperationResult

  sealed trait BookingOperationResult extends CommandReply

  //state
  final case class State(brokerActors: Map[String, ActorRef[Broker.Command]]) extends CborSerializable

  def buildId(customId: String): String = s"$TAG-$customId"

  def apply(clientData: ClientData, brokers:Map[String, ActorRef[Broker.Command]]): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(clientData.clientId),
        emptyState = State(brokers),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
        .withTagger(_=> Set(TAG))

    }
  }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case c: RemoveBroker => brokerTerminated(context, state, c)
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BrokerTerminated(brokerId, broker) =>
        context.log.info(s"Broker: [${brokerId}] has been terminated. Removing from Client.")
        context.unwatch(broker)
        State(state.brokerActors - brokerId)
      case _ => state
    }
  }

  private def brokerTerminated(contxt: ActorContext[Command], state: State, cmd: RemoveBroker): Effect[Event, State] ={
    Effect.persist(BrokerTerminated(cmd.brokerId, cmd.broker))
  }
}
