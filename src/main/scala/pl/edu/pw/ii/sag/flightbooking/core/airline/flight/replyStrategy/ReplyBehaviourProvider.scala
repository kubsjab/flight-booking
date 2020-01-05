package pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}

abstract class ReplyBehaviourProvider {

  def reply[Event, ReplyMessage, State](replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State]

  def persistAndReply[Event, ReplyMessage, State](event: Event, replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State]

  def unhandledWithNoReply[Event, ReplyMessage, State](): ReplyEffect[Event, State] = {
    Effect.unhandled.thenNoReply()
  }

}
