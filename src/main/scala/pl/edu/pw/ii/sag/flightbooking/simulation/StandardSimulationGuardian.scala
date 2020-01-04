package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.AirlineManager
import pl.edu.pw.ii.sag.flightbooking.core.broker.BrokerManager
import pl.edu.pw.ii.sag.flightbooking.core.client.ClientManager
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object StandardSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.STANDARD

  final case class AirlinesGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class FlightsGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class BrokersGenerated(response: BrokerGenerator.OperationResult) extends Simulation.Message

  final case class ClientsGenerated(response: ClientGenerator.OperationResult) extends Simulation.Message

  /**
   * Behaviour of this Actor defines initial simulation setup. In general whole simulation consists of 3 main actor layers:
   * - airlines
   * - brokers
   * - clients
   *
   * Each airline offers some flight options, which can be booked by broker on client's request. Although it is safe to create
   * all airlines, brokers and clients independently, flights and booking requests should be created once all core actors
   * are properly configured and settled up. Otherwise race condition can occur during simulation setup.
   *
   */
  def apply(): Behavior[Simulation.Message] = Behaviors.setup[Simulation.Message] { context =>
    val airlineManager = context.spawn(AirlineManager(), "airline-manager")
    val airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult] = context.messageAdapter(rsp => AirlinesGenerated(rsp))
    val brokerManager = context.spawn(BrokerManager(), "broker-manager")
    val brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult] = context.messageAdapter(rsp => BrokersGenerated(rsp))
    val clientManager = context.spawn(ClientManager(), "client-manager")
    val clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult] = context.messageAdapter(rsp => ClientsGenerated(rsp))

    val airlineGenerator = context.spawn(AirlineGenerator(airlineManager), "airline-generator")
    val flightGenerator = context.spawn(FlightGenerator(airlineManager), "flight-generator")
    val brokerGenerator = context.spawn(BrokerGenerator(brokerManager, airlineManager), "broker-generator")
    val clientGenerator = context.spawn(ClientGenerator(clientManager, brokerManager), "client-generator")


    Behaviors.receiveMessage[Simulation.Message] {
      case Simulation.Start() =>
        simulationStarted(context, airlineGenerator, airlineGeneratorResponseWrapper)
      case AirlinesGenerated(response: AirlineGenerator.OperationResult) =>
        airlinesGenerated(context, flightGenerator, brokerGenerator, response, brokerGeneratorResponseWrapper)
      case BrokersGenerated(response: BrokerGenerator.OperationResult) =>
        brokersGenerated(context, clientGenerator, response, clientGeneratorResponseWrapper)
      case ClientsGenerated(response: ClientGenerator.OperationResult) =>
        clientsGenerated(context, response)
      case _ => Behaviors.same
    }

  }

  private def simulationStarted(context: ActorContext[Simulation.Message],
                                airlineGenerator: ActorRef[AirlineGenerator.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Behavior[Simulation.Message] = {
    generateAirlines(context, airlineGenerator, airlineGeneratorResponseWrapper, Configuration.airlinesCount)
    Behaviors.same
  }

  private def airlinesGenerated(context: ActorContext[Simulation.Message],
                                flightGenerator: ActorRef[FlightGenerator.Command],
                                brokerGenerator: ActorRef[BrokerGenerator.Command],
                                response: AirlineGenerator.OperationResult,
                                brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Behavior[Simulation.Message] = {
    response match {
      case AirlineGenerator.AirlineGenerationCompleted(airlineIds) if airlineIds.size == Configuration.airlinesCount =>
        generateFlights(context, flightGenerator, airlineIds)
        generateBrokers(context, brokerGenerator, airlineIds, brokerGeneratorResponseWrapper)
        Behaviors.same
      case AirlineGenerator.AirlineGenerationCompleted(_) =>
        context.log.error("Airlines count does not match expected airlines count")
        Behaviors.stopped
      case AirlineGenerator.Rejected(reason) =>
        context.log.error("Failed to generate airlines. " + reason)
        Behaviors.stopped
    }
  }

  private def brokersGenerated(context: ActorContext[Simulation.Message],
                               clientGenerator: ActorRef[ClientGenerator.Command],
                               response: BrokerGenerator.OperationResult,
                               clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Behavior[Simulation.Message] = {
    response match {
      case BrokerGenerator.BrokerGenerationCompleted(brokerIds) if brokerIds.size == Configuration.brokersCount =>
        generateClients(context, clientGenerator, brokerIds, clientGeneratorResponseWrapper)
        Behaviors.same
      case BrokerGenerator.BrokerGenerationCompleted(_) =>
        context.log.error("Brokers count does not match expected brokers count")
        Behaviors.stopped
      case BrokerGenerator.Rejected(reason) =>
        context.log.error("Failed to generate brokers. " + reason)
        Behaviors.stopped
    }
  }

  private def clientsGenerated(context: ActorContext[Simulation.Message],
                               response: ClientGenerator.OperationResult): Behavior[Simulation.Message] = {
    response match {
      case ClientGenerator.ClientGenerationCompleted(clientIds) if clientIds.size == Configuration.clientsCount =>
        // TODO Probably when brokers and clients (mainly clients) are created, we should create some initial booking requests (similarly to airlines and flights)
        Behaviors.same
      case ClientGenerator.ClientGenerationCompleted(_) =>
        context.log.error("Clients count does not match expected clients count")
        Behaviors.stopped
      case ClientGenerator.Rejected(reason) =>
        context.log.error("Failed to generate clients. " + reason)
        Behaviors.stopped
    }
  }

  private def generateAirlines(context: ActorContext[Simulation.Message],
                               airlineGenerator: ActorRef[AirlineGenerator.Command],
                               airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult],
                               airlinesCount: Int): Unit = {
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(airlinesCount, airlineGeneratorResponseWrapper)
  }

  private def generateFlights(context: ActorContext[Simulation.Message],
                              flightGenerator: ActorRef[FlightGenerator.Command],
                              airlineIds: Set[String]): Unit = {
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, Configuration.initialMinFlightCount, Configuration.initialMaxFlightCount)
    if (Configuration.flightSchedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledStandardFlightsGeneration(
        FlightGenerator.GenerateStandardFlights(airlineIds, Configuration.schedulerMinFlightCount, Configuration.schedulerMaxFlightCount),
        FiniteDuration(Configuration.flightSchedulerDelay, duration.SECONDS)
      )
    }
  }

  private def generateBrokers(context: ActorContext[Simulation.Message],
                              brokerGenerator: ActorRef[BrokerGenerator.Command],
                              airlineIds: Set[String],
                              brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Unit = {
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(
      Configuration.brokersCount, airlineIds, Configuration.minAirlinesInBrokerCount, Configuration.maxAirlinesInBrokerCount, brokerGeneratorResponseWrapper)
  }

  private def generateClients(context: ActorContext[Simulation.Message],
                              clientGenerator: ActorRef[ClientGenerator.Command],
                              brokersIds: Set[String],
                              clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Unit = {
    clientGenerator ! ClientGenerator.GenerateStandardClients(
      Configuration.clientsCount, brokersIds, Configuration.minBrokersInClientCount, Configuration.maxBrokersInClientCount, clientGeneratorResponseWrapper)
  }

}
