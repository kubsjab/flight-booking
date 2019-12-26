package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.data.FlightDataGenerator

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

object FlightGenerator {

  sealed trait Command extends CborSerializable
  final case class GenerateStandardFlights(airlineIds: Seq[String], minCount: Int, maxCount: Int) extends Command
  final case class GenerateOverbookingFlights(airlineIds: Seq[String], minCount: Int, maxCount: Int) extends Command
  final case class GenerateFlights(simulationType: SimulationType, airline: ActorRef[Airline.Command], minCount: Int, maxCount: Int) extends Command
  private final case class WrappedAirlineResponse(response: Airline.OperationResult) extends Command
  private final case class AdaptedAirlineManagerFailure(msg: String) extends Command


  def apply(airlineManager: ActorRef[AirlineManager.Command]): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    val airlineResponseWrapper: ActorRef[Airline.OperationResult] = context.messageAdapter(rsp => WrappedAirlineResponse(rsp))
    message match {
      case cmd: GenerateStandardFlights => standardFlightsGeneration(context, airlineManager, cmd)
      case cmd: GenerateOverbookingFlights => overbookingFlightsGeneration(context, airlineManager, cmd)
      case cmd: GenerateFlights => generateFlights(cmd, airlineResponseWrapper)
      case cmd: WrappedAirlineResponse => airlineMessageFailure(context, cmd)
      case cmd: AdaptedAirlineManagerFailure => airlineManagerMessageFailure(context, cmd)
      case _ => Behaviors.same
    }
  }

  private def standardFlightsGeneration(context: ActorContext[Command],
                                airlineManager: ActorRef[AirlineManager.Command],
                                        cmd: GenerateStandardFlights): Behavior[Command] = {
    context.log.info("Generating {}-{} standard flights for {} airlines", cmd.minCount, cmd.maxCount, cmd.airlineIds.size)
    val simulationType: SimulationType = SimulationType.STANDARD
    flightsGeneration(context, simulationType, airlineManager, cmd.airlineIds, cmd.minCount, cmd.maxCount)
  }

  private def overbookingFlightsGeneration(context: ActorContext[Command],
                                        airlineManager: ActorRef[AirlineManager.Command],
                                        cmd: GenerateOverbookingFlights): Behavior[Command] = {
    context.log.info("Generating {}-{} overbooking flights for {} airlines", cmd.minCount, cmd.maxCount, cmd.airlineIds.size)
    val simulationType: SimulationType = SimulationType.OVERBOOKING
    flightsGeneration(context, simulationType, airlineManager, cmd.airlineIds, cmd.minCount, cmd.maxCount)
  }

  private def flightsGeneration(context: ActorContext[Command],
                                simulationType: SimulationType,
                                airlineManager: ActorRef[AirlineManager.Command],
                                airlineIds: Seq[String],
                                minCount: Int,
                                maxCount: Int): Behavior[Command] = {
    implicit val timeout: Timeout = 5.seconds
    airlineIds.foreach(airlineId =>
      context.ask(airlineManager, (ref: ActorRef[AirlineManager.AirlineCollection]) => AirlineManager.GetAirline(airlineId, ref)) {
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.nonEmpty =>
          GenerateFlights(simulationType, airlines.head._2, minCount, maxCount)
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.isEmpty =>
          AdaptedAirlineManagerFailure(s"Received actor ref for airlineId: [$airlineId] is empty")
        case Failure(ex) =>
          AdaptedAirlineManagerFailure(s"Failed to get actor ref by airlineId: [$airlineId]. Reason: ${ex.toString}")
      }
    )
    Behaviors.same
  }


  private def airlineMessageFailure(context: ActorContext[Command], wrappedResponse: WrappedAirlineResponse): Behavior[Command]  ={
    wrappedResponse.response match {
      case Airline.FlightCreationConfirmed(airlineId) => context.log.debug(s"Flight - [${airlineId}] creation has been confirmed")
      case Airline.Rejected(reason) => context.log.warn(s"Airline creation has been rejected. Reason: $reason")
      case _ => throw new IllegalStateException(s"Unexpected response [${wrappedResponse.response}] from AirlineManager")
    }
    Behaviors.same
  }

  private def airlineManagerMessageFailure(context: ActorContext[Command], cmd: AdaptedAirlineManagerFailure): Behavior[Command]  ={
    context.log.warn(cmd.msg)
    Behaviors.same
  }

  private def generateFlights(cmd: GenerateFlights, airlineResponseWrapper: ActorRef[Airline.OperationResult]): Behavior[Command] = {
    val flightBookingStrategy = cmd.simulationType match {
      case SimulationType.STANDARD => FlightBookingStrategyType.STANDARD
      case SimulationType.OVERBOOKING => FlightBookingStrategyType.OVERBOOKING
    }

    val flightsCount = Random.between(cmd.minCount, cmd.maxCount)
    (0 to flightsCount).foreach(_ => {
      val flightData = FlightDataGenerator.generateRandomFlightData()
      cmd.airline ! Airline.CreateFlight(flightData, flightBookingStrategy, airlineResponseWrapper)
    })

    Behaviors.same
  }


}
