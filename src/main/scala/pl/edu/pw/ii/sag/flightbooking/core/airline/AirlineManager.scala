package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId


object AirlineManager {
  sealed trait Command
  sealed trait Event
  final case class State()

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("airline-manager"),
      emptyState = State(),
      commandHandler = (state, cmd) => ???,
      eventHandler = (state, evt) => ???)
}