package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineManager}
import pl.edu.pw.ii.sag.flightbooking.core.broker.{Broker, BrokerData, BrokerManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

object BrokerGenerator {

  // command
  sealed trait Command extends CborSerializable
  final case class GenerateStandardBrokers(count: Int,
                                           airlineIds: Set[String],
                                           minAirlinesInBrokerCount: Int,
                                           maxAirlinesInBrokerCount: Int,
                                           replyTo: ActorRef[OperationResult]) extends Command
  final case class AggregatedBrokers(brokerIds: Set[String], replyTo: ActorRef[OperationResult]) extends Command
  final case class GenerateBrokersWithAirlineReferences(context: ActorContext[Command],
                                                         brokerManager: ActorRef[BrokerManager.Command],
                                                         airlineManager: ActorRef[AirlineManager.Command],
                                                         count: Int,
                                                         airlineIds: Set[String],
                                                         airlineRefs: Map[String, ActorRef[Airline.Command]],
                                                         minAirlinesInBrokerCount: Int,
                                                         maxAirlinesInBrokerCount: Int,
                                                         replyToWhenCompleted: ActorRef[OperationResult]) extends Command

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class BrokerGenerationCompleted(brokerIds: Set[String]) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult


  def apply(brokerManager: ActorRef[BrokerManager.Command], airlineManager:ActorRef[AirlineManager.Command]): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardBrokers(count, airlineIds, minAirlinesInBrokerCount, maxAirlinesInBrokerCount, replyTo) =>
        generateBrokers(context, brokerManager, airlineManager, count, airlineIds, minAirlinesInBrokerCount, maxAirlinesInBrokerCount, replyTo)
      case  GenerateBrokersWithAirlineReferences(context: ActorContext[Command],
                                                 brokerManager: ActorRef[BrokerManager.Command],
                                                 airlineManager: ActorRef[AirlineManager.Command],
                                                 count: Int,
                                                 airlineIds: Set[String],
                                                 airlineRefs: Map[String, ActorRef[Airline.Command]],
                                                 minAirlinesInBrokerCount: Int,
                                                 maxAirlinesInBrokerCount: Int,
                                                 replyToWhenCompleted: ActorRef[OperationResult]) =>
        generateBrokersWithAirlineReferences(context: ActorContext[Command],
                                             brokerManager: ActorRef[BrokerManager.Command],
                                             airlineManager: ActorRef[AirlineManager.Command],
                                             count: Int,
                                             airlineIds: Set[String],
                                             airlineRefs: Map[String, ActorRef[Airline.Command]],
                                             minAirlinesInBrokerCount: Int,
                                             maxAirlinesInBrokerCount: Int,
                                             replyToWhenCompleted: ActorRef[OperationResult])
      case AggregatedBrokers(brokerIds, replyTo) => confirmBrokerGenerationCompletion(brokerIds, replyTo)
      case _ => Behaviors.same
    }
  }

  def standardBrokerGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard brokers", count)
    Behaviors.same
  }
  private def generateBrokers(context: ActorContext[Command],
                               brokerManager: ActorRef[BrokerManager.Command],
                               airlineManager: ActorRef[AirlineManager.Command],
                               count: Int,
                               airlineIds: Set[String],
                               minAirlinesInBrokerCount: Int,
                               maxAirlinesInBrokerCount: Int,
                               replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] = {

    context.log.info("Generating {} brokers", count)
    implicit val timeout: Timeout = 5.seconds
    context.ask(airlineManager, (ref: ActorRef[AirlineManager.AirlineCollection]) => AirlineManager.GetAirlines(ref)) {
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.nonEmpty =>
          GenerateBrokersWithAirlineReferences(context, brokerManager, airlineManager, count, airlineIds, airlines, minAirlinesInBrokerCount, maxAirlinesInBrokerCount, replyToWhenCompleted)
        case Success(AirlineManager.AirlineCollection(airlines: Map[String, ActorRef[Airline.Command]])) if airlines.isEmpty =>
          throw new Exception(s"No airline actors")
        case Failure(ex) =>
          throw new Exception(s"Failed to get airline actors references. Reason: ${ex.toString}")
      }

    Behaviors.same
  }

  private def generateBrokersWithAirlineReferences(context: ActorContext[Command],
                                                   brokerManager: ActorRef[BrokerManager.Command],
                                                   airlineManager: ActorRef[AirlineManager.Command],
                                                   count: Int,
                                                   airlineIds: Set[String],
                                                   airlineRefs: Map[String, ActorRef[Airline.Command]],
                                                   minAirlinesInBrokerCount: Int,
                                                   maxAirlinesInBrokerCount: Int,
                                                   replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] ={
    context.spawnAnonymous(
      Aggregator[BrokerManager.OperationResult, AggregatedBrokers](
        sendRequests = { replyTo =>
          (1 to count).foreach(i => {
            val indices = Random.shuffle(0 to (airlineIds.size)-1).toList
              .take(minAirlinesInBrokerCount + Random.nextInt(maxAirlinesInBrokerCount-minAirlinesInBrokerCount))
            val airlineIdsForBroker = indices.map(airlineIds.toList).toSet
            val airlineRefsForBroker = airlineRefs.filter(_._1 == airlineIdsForBroker)
            brokerManager ! BrokerManager.CreateBroker(BrokerData(Broker.buildId(i.toString), s"Broker-$i", airlineIdsForBroker), airlineRefsForBroker, replyTo)
          })
        },
        expectedReplies = count,
        context.self,
        aggregateReplies = replies =>
          AggregatedBrokers(
            replies
              .filter(_.isInstanceOf[BrokerManager.BrokerCreationConfirmed])
              .map(x => x.asInstanceOf[BrokerManager.BrokerCreationConfirmed].brokerId)
              .toSet,
            replyToWhenCompleted),
        timeout = 5.seconds))
    Behaviors.same
  }

  private def confirmBrokerGenerationCompletion(brokerIds: Set[String], replyTo: ActorRef[OperationResult]): Behavior[Command] ={
    replyTo ! BrokerGenerator.BrokerGenerationCompleted(brokerIds)
    Behaviors.same
  }
}
