package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

case class ClientData(clientId: String, name: String, brokerIds: Set[String])
object Client {
  // command
  sealed trait Command extends CborSerializable

  // event
  sealed trait Event extends CborSerializable

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class Accepted() extends OperationResult
  final case class Rejected(reason: String) extends OperationResult

  sealed trait BookingOperationResult extends CommandReply

  //state
  final case class State(brokerActors: Map[String, ActorRef[Broker.Command]]) extends CborSerializable

  def buildId(customId: String): String = s"client-$customId"

  def apply(clientData: ClientData): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(clientData.clientId),
        emptyState = State(Map.empty),
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
      case _ => state
    }
  }

}
