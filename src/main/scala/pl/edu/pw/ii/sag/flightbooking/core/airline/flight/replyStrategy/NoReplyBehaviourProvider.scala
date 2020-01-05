package pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}

class NoReplyBehaviourProvider extends ReplyBehaviourProvider {

  override def reply[Event, ReplyMessage, State](replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    Effect.noReply
  }

  override def persistAndReply[Event, ReplyMessage, State](event: Event, replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    Effect
      .persist(event)
      .thenNoReply()
  }
}
