package pl.edu.pw.ii.sag.flightbooking.simulation.generation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object ClientGenerator {

  sealed trait Command

  final case class GenerateStandardClients(count: Int) extends Command


  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardClients(count) => standardClientGeneration(context, count)
      case _ => Behaviors.same
    }
  }

  def standardClientGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard clients", count)
    Behaviors.same
  }

}

