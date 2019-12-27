package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.AirlineManager
import pl.edu.pw.ii.sag.flightbooking.core.broker.BrokerManager
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

object StandardSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.STANDARD

  final case class AirlinesGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class FlightsGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class BrokersGenerated(response: BrokerGenerator.OperationResult) extends Simulation.Message

  final case class ClientsGenerated() extends Simulation.Message

  // TODO Move params to configuration https://doc.akka.io/docs/akka/current/extending-akka.html#extending-akka-settings
  val airlinesCount = 3
  val minFlightCount = 10
  val maxFlightCount = 20
  val brokersCount = 10
  val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
  val maxAirlinesInBrokerCount: Int = airlinesCount
  val clientsCount = 100

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

    Behaviors.receiveMessage[Simulation.Message] {
      case Simulation.Start() =>
        simulationStarted(context, airlineManager, airlineGeneratorResponseWrapper)
      case AirlinesGenerated(response: AirlineGenerator.OperationResult) =>
        airlinesGenerated(context, airlineManager, response)
      case BrokersGenerated(response: BrokerGenerator.OperationResult) =>
        brokersGenerated(context, response)
      case _ => Behaviors.same
    }

  }

  private def simulationStarted(context: ActorContext[Simulation.Message],
                                airlineManager: ActorRef[AirlineManager.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Behavior[Simulation.Message] = {
    generateAirlines(context, airlineManager, airlineGeneratorResponseWrapper, airlinesCount)
    Behaviors.same
  }

  private def generateAirlines(context: ActorContext[Simulation.Message],
                               airlineManager: ActorRef[AirlineManager.Command],
                               airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult],
                               airlinesCount: Int): Unit = {
    val airlineGenerator = context.spawn(AirlineGenerator(airlineManager), "airline-generator")
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(airlinesCount, airlineGeneratorResponseWrapper)
  }

  private def airlinesGenerated(context: ActorContext[Simulation.Message],
                                airlineManager: ActorRef[AirlineManager.Command],
                                response: AirlineGenerator.OperationResult): Behavior[Simulation.Message] = {
    response match {
      case AirlineGenerator.AirlineGenerationCompleted(airlineIds) if airlineIds.size == airlinesCount =>
        generateFlights(context, airlineManager, airlineIds, minFlightCount, maxFlightCount)
        val brokerManager = context.spawn(BrokerManager(), "broker-manager")
        val brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult] = context.messageAdapter(rsp => BrokersGenerated(rsp))
        generateBrokers(context, brokerManager, airlineIds, brokersCount, minAirlinesInBrokerCount, maxAirlinesInBrokerCount, brokerGeneratorResponseWrapper)
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
                                response: BrokerGenerator.OperationResult): Behavior[Simulation.Message] = {
    response match {
      case BrokerGenerator.BrokerGenerationCompleted(brokerIds) if brokerIds.size == brokersCount =>
        generateClients(context, clientsCount) // TODO Probably when brokers and clients (mainly clients) are created, we should create some initial booking requests (similarly to airlines and flights)
        Behaviors.same
      case BrokerGenerator.BrokerGenerationCompleted(_) =>
        context.log.error("Brokers count does not match expected brokers count")
        Behaviors.stopped
      case BrokerGenerator.Rejected(reason) =>
        context.log.error("Failed to generate brokers. " + reason)
        Behaviors.stopped
    }
  }

  private def generateFlights(context: ActorContext[Simulation.Message],
                              airlineManager: ActorRef[AirlineManager.Command],
                              airlineIds: Set[String],
                              minFlightCount: Int,
                              maxFlightCount: Int): Unit = {
    val flightGenerator = context.spawn(FlightGenerator(airlineManager), "flight-generator")
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, minFlightCount, maxFlightCount)
  }

  private def generateBrokers(context: ActorContext[Simulation.Message],
                              brokerManager: ActorRef[BrokerManager.Command],
                              airlineIds: Set[String],
                              brokersCount: Int,
                              minAirlinesInBrokerCount: Int,
                              maxAirlinesInBrokerCount: Int,
                              brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Unit = {
    val brokerGenerator = context.spawn(BrokerGenerator(brokerManager), "broker-generator")
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(brokersCount, airlineIds, minAirlinesInBrokerCount, maxAirlinesInBrokerCount, brokerGeneratorResponseWrapper)
  }

  private def generateClients(context: ActorContext[Simulation.Message], clientsCount: Int): Unit = {
    val clientGenerator = context.spawn(ClientGenerator(), "client-generator")
    clientGenerator ! ClientGenerator.GenerateStandardClients(clientsCount)
  }

}
