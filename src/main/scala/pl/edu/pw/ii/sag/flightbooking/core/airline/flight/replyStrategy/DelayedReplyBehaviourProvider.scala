package pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class DelayedReplyBehaviourProvider[Command](val context: ActorContext[Command], minDelayInSeconds: Int, maxDelayInSeconds: Int) extends ReplyBehaviourProvider {

  val minDelayMs: Int = minDelayInSeconds * 1000
  val maxDelayMs: Int = maxDelayInSeconds * 1000

  override def reply[Event, ReplyMessage, State](replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    scheduleReply(replyTo, message)
    Effect.noReply
  }

  override def persistAndReply[Event, ReplyMessage, State](event: Event, replyTo: ActorRef[ReplyMessage], message: ReplyMessage): ReplyEffect[Event, State] = {
    scheduleReply(replyTo, message)
    Effect
      .persist(event)
      .thenNoReply()
  }

  private def scheduleReply[ReplyMessage](replyTo: ActorRef[ReplyMessage], message: ReplyMessage): Unit = {
    val delay = minDelayMs + Random.nextInt(maxDelayMs - minDelayMs + 1)

    context.scheduleOnce(
      FiniteDuration(delay, duration.MILLISECONDS),
      replyTo,
      message
    )

  }
}
