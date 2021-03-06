package pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy

import akka.actor.typed.scaladsl.ActorContext
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyStrategyType.ReplyStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration

object ReplyBehaviourProviderFactory {

  val minDelayInSeconds: Int = Configuration.Simulation.Delayed.delayedFlight.minDelay
  val maxDelayInSeconds: Int = Configuration.Simulation.Delayed.delayedFlight.maxDelay

  def create[Command](context: ActorContext[Command], anomalyType: ReplyStrategyType): ReplyBehaviourProvider = {
    anomalyType match {
      case ReplyStrategyType.NORMAL => new StandardReplyBehaviourProvider()
      case ReplyStrategyType.DELAY => new DelayedReplyBehaviourProvider(context, minDelayInSeconds, maxDelayInSeconds)
      case ReplyStrategyType.IGNORE => new NoReplyBehaviourProvider()
    }
  }

}
