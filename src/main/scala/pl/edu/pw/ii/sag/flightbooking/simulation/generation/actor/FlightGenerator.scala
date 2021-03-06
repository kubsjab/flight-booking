package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.data.FlightDataGenerator

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

object FlightGenerator {

  sealed trait Command extends CborSerializable

  sealed trait GenerateFlights extends Command
  final case class GenerateStandardFlights(airlineIds: Set[String], minCount: Int, maxCount: Int) extends GenerateFlights
  final case class GenerateDelayedFlights(airlineIds: Set[String], minCount: Int, maxCount: Int) extends GenerateFlights
  final case class GenerateOverbookingFlights(airlineIds: Set[String], minCount: Int, maxCount: Int) extends GenerateFlights

  final case class InitScheduledStandardFlightsGeneration(scheduledCmd: GenerateStandardFlights, delay: FiniteDuration) extends Command
  final case class InitScheduledDelayedFlightsGeneration(scheduledCmd: GenerateDelayedFlights, delay: FiniteDuration) extends Command
  final case class InitScheduledOverbookingFlightsGeneration(scheduledCmd: GenerateOverbookingFlights, delay: FiniteDuration) extends Command

  private final case class GenerateFlightsForAirline(simulationType: SimulationType, airline: ActorRef[Airline.Command], minCount: Int, maxCount: Int) extends Command

  private final case class WrappedAirlineResponse(response: Airline.OperationResult) extends Command
  private final case class AdaptedAirlineManagerFailure(msg: String) extends Command

  private trait TimerKey
  private case object StandardFlightTimerKey extends TimerKey
  private case object DelayedFlightTimerKey extends TimerKey
  private case object OverbookingFlightTimerKey extends TimerKey

  def apply(airlineManager: ActorRef[AirlineManager.Command]): Behavior[Command] = Behaviors.withTimers(timers =>
    Behaviors.receive { (context, message: Command) =>
      val airlineResponseWrapper: ActorRef[Airline.OperationResult] = context.messageAdapter(rsp => WrappedAirlineResponse(rsp))
      message match {
        case cmd: GenerateStandardFlights => standardFlightsGeneration(context, airlineManager, cmd)
        case cmd: GenerateDelayedFlights => delayedFlightsGeneration(context, airlineManager, cmd)
        case cmd: GenerateOverbookingFlights => overbookingFlightsGeneration(context, airlineManager, cmd)
        case cmd: InitScheduledStandardFlightsGeneration => scheduledFlightGeneration(context, timers, cmd.scheduledCmd, cmd.delay, StandardFlightTimerKey)
        case cmd: InitScheduledDelayedFlightsGeneration => scheduledFlightGeneration(context, timers, cmd.scheduledCmd, cmd.delay, DelayedFlightTimerKey)
        case cmd: InitScheduledOverbookingFlightsGeneration => scheduledFlightGeneration(context, timers, cmd.scheduledCmd, cmd.delay, OverbookingFlightTimerKey)
        case cmd: GenerateFlightsForAirline => generateFlightsForAirline(cmd, airlineResponseWrapper)
        case cmd: WrappedAirlineResponse => handleAirlineResponse(context, cmd)
        case cmd: AdaptedAirlineManagerFailure => airlineManagerMessageFailure(context, cmd)
        case _ => Behaviors.same
      }
    }

  )

  private def standardFlightsGeneration(context: ActorContext[Command],
                                        airlineManager: ActorRef[AirlineManager.Command],
                                        cmd: GenerateStandardFlights): Behavior[Command] = {
    context.log.info("Generating {}-{} standard flights for {} airlines", cmd.minCount, cmd.maxCount, cmd.airlineIds.size)
    val simulationType: SimulationType = SimulationType.STANDARD
    generateFlights(context, simulationType, airlineManager, cmd.airlineIds, cmd.minCount, cmd.maxCount)
    Behaviors.same
  }

  private def delayedFlightsGeneration(context: ActorContext[Command],
                                       airlineManager: ActorRef[AirlineManager.Command],
                                       cmd: GenerateDelayedFlights): Behavior[Command] = {
    context.log.info("Generating {}-{} delayed flights for {} airlines", cmd.minCount, cmd.maxCount, cmd.airlineIds.size)
    val simulationType: SimulationType = SimulationType.DELAYED
    generateFlights(context, simulationType, airlineManager, cmd.airlineIds, cmd.minCount, cmd.maxCount)
    Behaviors.same
  }

  private def overbookingFlightsGeneration(context: ActorContext[Command],
                                           airlineManager: ActorRef[AirlineManager.Command],
                                           cmd: GenerateOverbookingFlights): Behavior[Command] = {
    context.log.info("Generating {}-{} overbooking flights for {} airlines", cmd.minCount, cmd.maxCount, cmd.airlineIds.size)
    val simulationType: SimulationType = SimulationType.OVERBOOKING
    generateFlights(context, simulationType, airlineManager, cmd.airlineIds, cmd.minCount, cmd.maxCount)
    Behaviors.same
  }

  private def scheduledFlightGeneration(context: ActorContext[Command],
                                        timers: TimerScheduler[Command],
                                        cmd: GenerateFlights,
                                        delay: FiniteDuration,
                                        timerKey: TimerKey): Behavior[Command] = {
    if (!timers.isTimerActive(timerKey)) {
      timers.startTimerWithFixedDelay(timerKey, cmd, delay)
    }
    Behaviors.same
  }

  private def generateFlights(context: ActorContext[Command],
                              simulationType: SimulationType,
                              airlineManager: ActorRef[AirlineManager.Command],
                              airlineIds: Set[String],
                              minCount: Int,
                              maxCount: Int): Unit = {
    implicit val timeout: Timeout = 5.seconds
    airlineIds.foreach(airlineId =>
      context.ask(airlineManager, (ref: ActorRef[AirlineManager.AirlineCollection]) => AirlineManager.GetAirline(airlineId, ref)) {
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.nonEmpty =>
          GenerateFlightsForAirline(simulationType, airlines.head._2, minCount, maxCount)
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.isEmpty =>
          AdaptedAirlineManagerFailure(s"Received actor ref for airlineId: [$airlineId] is empty")
        case Failure(ex) =>
          AdaptedAirlineManagerFailure(s"Failed to get actor ref by airlineId: [$airlineId]. Reason: ${ex.toString}")
      }
    )
  }


  private def handleAirlineResponse(context: ActorContext[Command], wrappedResponse: WrappedAirlineResponse): Behavior[Command] = {
    wrappedResponse.response match {
      case Airline.FlightCreationConfirmed(airlineId) => context.log.debug(s"Flight - [${airlineId}] creation has been confirmed")
      case Airline.Rejected(reason) => context.log.warn(s"Airline creation has been rejected. Reason: $reason")
      case _ => throw new IllegalStateException(s"Unexpected response [${wrappedResponse.response}] from AirlineManager")
    }
    Behaviors.same
  }

  private def airlineManagerMessageFailure(context: ActorContext[Command], cmd: AdaptedAirlineManagerFailure): Behavior[Command] = {
    context.log.warn(cmd.msg)
    Behaviors.same
  }

  private def generateFlightsForAirline(cmd: GenerateFlightsForAirline, airlineResponseWrapper: ActorRef[Airline.OperationResult]): Behavior[Command] = {
    val flightBookingStrategy = cmd.simulationType match {
      case SimulationType.STANDARD => FlightBookingStrategyType.STANDARD
      case SimulationType.OVERBOOKING => FlightBookingStrategyType.OVERBOOKING
      case SimulationType.DELAYED => FlightBookingStrategyType.STANDARD
    }

    val replyStrategy = cmd.simulationType match {
      case SimulationType.STANDARD => ReplyStrategyType.NORMAL
      case SimulationType.OVERBOOKING => ReplyStrategyType.NORMAL
      case SimulationType.DELAYED => ReplyStrategyType.DELAY
    }


    val flightsCount = cmd.minCount + Random.nextInt(cmd.maxCount - cmd.minCount + 1)
    if (flightsCount > 0) {
      (0 to flightsCount).foreach(_ => {
        val flightInfo = FlightDataGenerator.generateRandomFlightInfo()
        cmd.airline ! Airline.CreateFlight(flightInfo, flightBookingStrategy, replyStrategy, airlineResponseWrapper)
      })
    }
    Behaviors.same
  }


}
