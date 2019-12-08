package pl.edu.pw.ii.sag.flightbooking.simulation.generation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object AirlineGenerator {

  sealed trait Command

  final case class GenerateStandardAirlines(count: Int) extends Command

  final case class GenerateOverbookingAirlines(count: Int) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardAirlines(count) => standardAirlineGeneration(context, count)
      case GenerateOverbookingAirlines(count) => overbookingAirlineGeneration(context, count)
      case _ => Behaviors.same
    }
  }

  def standardAirlineGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard airlines", count)
    Behaviors.same
  }

  def overbookingAirlineGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} airlines with overbooking", count)
    Behaviors.same
  }

}

