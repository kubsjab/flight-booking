package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object WithDelaySimulationGuardian extends Simulation {
  override val simulationType: SimulationType = SimulationType.DELAYED
  private val initialAgentCount = InitialAgentCount(
    Configuration.Simulation.Delayed.airlinesCount,
    Configuration.Simulation.Delayed.brokersCount,
    Configuration.Simulation.Delayed.clientsCount
  )

  def apply(): Behavior[Simulation.Message] = new WithDelaySimulationGuardian(initialAgentCount).apply()
}

class WithDelaySimulationGuardian(initialAgentCount: InitialAgentCount) extends AbstractSimulationGuardian(initialAgentCount) {

  private val config = Configuration.Simulation.Delayed

  override def generateAirlines(context: ActorContext[Simulation.Message],
                                airlineGenerator: ActorRef[AirlineGenerator.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Unit = {
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(config.airlinesCount, airlineGeneratorResponseWrapper)
  }

  override def generateBrokers(context: ActorContext[Simulation.Message],
                               brokerGenerator: ActorRef[BrokerGenerator.Command],
                               airlineIds: Set[String],
                               brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Unit = {
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(
      config.brokersCount, airlineIds, config.minAirlinesInBrokerCount, config.maxAirlinesInBrokerCount, brokerGeneratorResponseWrapper)
  }

  override def generateClients(context: ActorContext[Simulation.Message],
                               clientGenerator: ActorRef[ClientGenerator.Command],
                               brokersIds: Set[String],
                               clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Unit = {
    clientGenerator ! ClientGenerator.GenerateStandardClients(
      config.clientsCount, brokersIds, config.minBrokersInClientCount, config.maxBrokersInClientCount, clientGeneratorResponseWrapper)
  }

  override def generateFlights(context: ActorContext[Simulation.Message],
                               flightGenerator: ActorRef[FlightGenerator.Command],
                               airlineIds: Set[String]): Unit = {
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, config.standardFlight.initialMinCount, config.standardFlight.initialMaxCount)
    flightGenerator ! FlightGenerator.GenerateDelayedFlights(airlineIds, config.delayedFlight.initialMinCount, config.delayedFlight.initialMaxCount)

    if (config.standardFlight.schedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledStandardFlightsGeneration(
        FlightGenerator.GenerateStandardFlights(airlineIds, config.standardFlight.schedulerMinCount, config.standardFlight.schedulerMaxCount),
        FiniteDuration(config.standardFlight.schedulerDelay, duration.SECONDS)
      )
    }
    if (config.delayedFlight.schedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledDelayedFlightsGeneration(
        FlightGenerator.GenerateDelayedFlights(airlineIds, config.delayedFlight.schedulerMinCount, config.delayedFlight.schedulerMaxCount),
        FiniteDuration(config.delayedFlight.schedulerDelay, duration.SECONDS)
      )
    }
  }
}
