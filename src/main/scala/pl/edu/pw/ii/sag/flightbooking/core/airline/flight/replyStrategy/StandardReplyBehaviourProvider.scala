package pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}

class StandardReplyBehaviourProvider extends ReplyBehaviourProvider {

  override def reply[Event, ReplyMessage, State](replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    Effect.reply(replyTo)(message)
  }

  override def persistAndReply[Event, ReplyMessage, State](event: Event, replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    Effect
      .persist(event)
      .thenReply(replyTo)(_ => message)
  }

}
