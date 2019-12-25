package pl.edu.pw.ii.sag.flightbooking.simulation.generation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineData, AirlineManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

object AirlineGenerator {

  sealed trait Command extends CborSerializable
  final case class GenerateStandardAirlines(count: Int) extends Command
  private final case class WrappedAirlineManagerResponse(response: AirlineManager.OperationResult) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardAirlines(count) => airlineGeneration(context, count)
      case WrappedAirlineManagerResponse(response) => airlineManagerResponseMapper(context, response)
      case _ => Behaviors.same
    }
  }

  private def airlineGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} airlines", count)
    val airlineManagerResponseWrapper: ActorRef[AirlineManager.OperationResult] = context.messageAdapter(rsp => WrappedAirlineManagerResponse(rsp))

    val airlineManager = context.spawn(AirlineManager(), "airline-generator")

    for (i <- 0 to count) {
      airlineManager ! AirlineManager.CreateAirline(AirlineData(Airline.buildId(i.toString), s"Airline-$i"), airlineManagerResponseWrapper)
    }

    Behaviors.same
  }

  private def airlineManagerResponseMapper(context: ActorContext[Command],
                                           response: AirlineManager.OperationResult): Behavior[Command] = {
    response match {
      case AirlineManager.AirlineCreationConfirmed(airlineId) => context.log.debug(s"Airline - [${airlineId}] creation has been confirmed")
      case AirlineManager.Rejected(reason) => context.log.warn(s"Airline creation has been rejected. Reason: $reason")
      case _ => throw new IllegalStateException(s"Unexpected response [$response] from AirlineManager")
    }
    Behaviors.same
  }

}

