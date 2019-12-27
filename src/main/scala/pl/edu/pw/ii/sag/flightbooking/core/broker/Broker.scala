package pl.edu.pw.ii.sag.flightbooking.core.broker

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class BrokerData(brokerId: String, name: String, airlineIds: Set[String])

object Broker {
  // command
  sealed trait Command extends CborSerializable

  // event
  sealed trait Event extends CborSerializable

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply

  //state
  final case class State() extends CborSerializable

  def buildId(customId: String): String = s"broker-$customId"

  def apply(brokerData: BrokerData): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(brokerData.brokerId),
        emptyState = State(),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
    }
  }

  private def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      cmd match {
        case _ => Effect.none
      }
  }

  private def eventHandler(context: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case _ => State()
    }
  }

}

