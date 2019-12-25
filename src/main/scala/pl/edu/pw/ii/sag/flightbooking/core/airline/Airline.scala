package pl.edu.pw.ii.sag.flightbooking.core.airline

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior

case class AirlineData(airlineId: String, name: String)


object Airline {
  sealed trait Command
  sealed trait Event
  final case class State()

  def buildId(customId: String): String = s"airline-$customId"

  def apply(airlineData: AirlineData): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(airlineData.airlineId),
      emptyState = State(),
      commandHandler = (state, cmd) => ???,
      eventHandler = (state, evt) => ???)
}