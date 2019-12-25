package pl.edu.pw.ii.sag.flightbooking.simulation.generation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

object FlightGenerator {

  sealed trait Command extends CborSerializable

  final case class GenerateStandardFlights(minCount: Int, maxCount: Int) extends Command
  final case class GenerateOverbookingFlights(minCount: Int, maxCount: Int) extends Command


  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardFlights(minCount, maxCount) => standardFlightsGeneration(context, minCount, maxCount)
      case _ => Behaviors.same
    }
  }

  def standardFlightsGeneration(context: ActorContext[Command], minCount: Int, maxCount: Int): Behavior[Command] = {
    context.log.info("Generating {}-{} standard flights for each Airline", minCount, maxCount)
    Behaviors.same
  }

}

