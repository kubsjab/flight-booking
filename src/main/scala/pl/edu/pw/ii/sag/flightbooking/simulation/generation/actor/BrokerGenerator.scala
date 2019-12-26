package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object BrokerGenerator {

  sealed trait Command

  final case class GenerateStandardBrokers(count: Int) extends Command


  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardBrokers(count) => standardBrokerGeneration(context, count)
      case _ => Behaviors.same
    }
  }

  def standardBrokerGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard brokers", count)
    Behaviors.same
  }

}
