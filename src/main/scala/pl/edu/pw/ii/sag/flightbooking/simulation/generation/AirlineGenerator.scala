package pl.edu.pw.ii.sag.flightbooking.simulation.generation

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineData, AirlineManager}

object AirlineGenerator {

  sealed trait Command
  final case class GenerateStandardAirlines(count: Int) extends Command
  final case class GenerateOverbookingAirlines(count: Int) extends Command
  private final case class WrappedAirlineManagerResponse(response: AirlineManager.OperationResult) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardAirlines(count) => standardAirlineGeneration(context, count)
      case GenerateOverbookingAirlines(count) => overbookingAirlineGeneration(context, count)
      case WrappedAirlineManagerResponse(response) => airlineManagerResponseMapper(context, response)
      case _ => Behaviors.same
    }
  }

  def standardAirlineGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard airlines", count)
    val airlineManagerResponseWrapper: ActorRef[AirlineManager.OperationResult] =
      context.messageAdapter(rsp => WrappedAirlineManagerResponse(rsp))

    val airlineManager = context.spawn(AirlineManager(), "airline-generator")

    for (i <- 0 to count){
      airlineManager ! AirlineManager.CreateAirline(AirlineData(Airline.buildId(i.toString), s"Airline-$i"), airlineManagerResponseWrapper)
    }

    Behaviors.same
  }

  def overbookingAirlineGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} airlines with overbooking", count)
    Behaviors.same
  }

  def airlineManagerResponseMapper(context: ActorContext[Command], response: AirlineManager.OperationResult): Behavior[Command] = {
    response match {
      case AirlineManager.AirlineCreationConfirmed(airlineId) => context.log.debug(s"Airline - [$airlineId] creation has been confirmed")
      case AirlineManager.Rejected(reason) => context.log.warn(s"Airline creation has been rejected. Reason: $reason")
      case _ => throw new IllegalStateException(s"Unexpected response [$response] from AirlineManager")
    }
    Behaviors.same
  }

}

