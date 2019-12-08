package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior

object Client {
  sealed trait Command
  sealed trait Event
  final case class State()

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("client"),
      emptyState = State(),
      commandHandler = (state, cmd) => ???,
      eventHandler = (state, evt) => ???)
}
