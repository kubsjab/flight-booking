package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import pl.edu.pw.ii.sag.flightbooking.core.broker.BrokerManager.BrokerCollection
import pl.edu.pw.ii.sag.flightbooking.core.broker.{Broker, BrokerManager}
import pl.edu.pw.ii.sag.flightbooking.core.client.{Client, ClientData, ClientManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

object ClientGenerator {

  // command
  sealed trait Command extends CborSerializable
  final case class GenerateStandardClients(count: Int,
                                           brokerIds: Set[String],
                                           minBrokersInClientCount: Int,
                                           maxBrokersInClientCount: Int,
                                           replyTo: ActorRef[OperationResult]) extends Command
  final case class AggregatedClients(clientsIds: Set[String], replyTo: ActorRef[OperationResult]) extends Command
  final case class GenerateClientsWithBrokerReferences(context: ActorContext[Command],
                                                       clientManager: ActorRef[ClientManager.Command],
                                                       brokerManager: ActorRef[BrokerManager.Command],
                                                       count: Int,
                                                       brokerIds: Set[String],
                                                       brokerRefs: Map[String, ActorRef[Broker.Command]],
                                                       minBrokersInClientCount: Int,
                                                       maxBrokersInClientCount: Int,
                                                       replyToWhenCompleted: ActorRef[OperationResult]) extends Command
  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class ClientGenerationCompleted(clientsIds: Set[String]) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult


  def apply(clientManager: ActorRef[ClientManager.Command], brokerManager:ActorRef[BrokerManager.Command]): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardClients(count, brokerIds, minBrokersInClientCount, maxBrokersInAirlinesCount, replyTo) =>
        generateClients(context, clientManager, brokerManager, count, brokerIds, minBrokersInClientCount, maxBrokersInAirlinesCount, replyTo)
      case  GenerateClientsWithBrokerReferences(context: ActorContext[Command],
                                                clientManager: ActorRef[ClientManager.Command],
                                                brokerManager: ActorRef[BrokerManager.Command],
                                                count: Int,
                                                brokerIds: Set[String],
                                                brokerRefs: Map[String, ActorRef[Broker.Command]],
                                                minBrokersInClientCount: Int,
                                                maxBrokersInClientCount: Int,
                                                replyToWhenCompleted: ActorRef[OperationResult]) =>
            generateClientsWithBrokerReferences(context: ActorContext[Command],
                                                clientManager: ActorRef[ClientManager.Command],
                                                brokerManager: ActorRef[BrokerManager.Command],
                                                count: Int,
                                                brokerIds: Set[String],
                                                brokerRefs: Map[String, ActorRef[Broker.Command]],
                                                minBrokersInClientCount: Int,
                                                maxBrokersInClientCount: Int,
                                                replyToWhenCompleted: ActorRef[OperationResult])
      case AggregatedClients(clientsIds, replyTo) => confirmClientGenerationCompletion(clientsIds, replyTo)
      case _ => Behaviors.same
    }
  }

  def standardClientGeneration(context: ActorContext[Command], count: Int): Behavior[Command] = {
    context.log.info("Generating {} standard clients", count)
    Behaviors.same
  }
  private def generateClients(context: ActorContext[Command],
                              clientManager: ActorRef[ClientManager.Command],
                              brokerManager: ActorRef[BrokerManager.Command],
                              count: Int,
                              brokerIds: Set[String],
                              minBrokersInClientCount: Int,
                              maxBrokersInClientCount: Int,
                              replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] = {
    context.log.info("Generating {} clients", count)
    implicit val timeout: Timeout = 5.seconds
    context.ask(brokerManager, (ref: ActorRef[BrokerManager.BrokerCollection]) => BrokerManager.GetBrokers(ref)) {
      case Success(BrokerManager.BrokerCollection(brokers: Map[String, ActorRef[Broker.Command]])) if brokers.nonEmpty =>
        GenerateClientsWithBrokerReferences(context, clientManager, brokerManager, count, brokerIds, brokers, minBrokersInClientCount, maxBrokersInClientCount, replyToWhenCompleted)
      case Success(BrokerManager.BrokerCollection(brokers: Map[String, ActorRef[Broker.Command]])) if brokers.isEmpty =>
        throw new Exception(s"No broker actors")
      case Failure(ex) =>
        throw new Exception(s"Failed to get broker actors references. Reason: ${ex.toString}")
    }
    Behaviors.same
  }

  private def generateClientsWithBrokerReferences(context: ActorContext[Command],
                                                  clientManager: ActorRef[ClientManager.Command],
                                                  brokerManager: ActorRef[BrokerManager.Command],
                                                  count: Int,
                                                  brokerIds: Set[String],
                                                  brokerRefs: Map[String, ActorRef[Broker.Command]],
                                                  minBrokersInClientCount: Int,
                                                  maxBrokersInClientCount: Int,
                                                  replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] = {
    context.spawnAnonymous(
      Aggregator[ClientManager.OperationResult, AggregatedClients](
        sendRequests = { replyTo =>
          (1 to count).foreach(i => {
            val indices = Random.shuffle(0 to (brokerIds.size)-1).toList
              .take(minBrokersInClientCount + Random.nextInt(maxBrokersInClientCount-minBrokersInClientCount))
            val brokerIdsForClient: Set[String] = indices.map(brokerIds.toList).toSet
            val brokerRefsForClient = brokerRefs.filter(entry => brokerIdsForClient.contains(entry._1))
            context.log.info(brokerIdsForClient.toString())
            context.log.info(brokerRefsForClient.toString())
            clientManager ! ClientManager.CreateClient(ClientData(Client.buildId(i.toString), s"Client-$i", brokerIdsForClient), brokerRefsForClient, replyTo)
          })
        },
        expectedReplies = count,
        context.self,
        aggregateReplies = replies =>
          AggregatedClients(
            replies
              .filter(_.isInstanceOf[ClientManager.ClientCreationConfirmed])
              .map(x => x.asInstanceOf[ClientManager.ClientCreationConfirmed].clientId)
              .toSet,
            replyToWhenCompleted),
        timeout = 5.seconds))
    Behaviors.same
  }

  private def confirmClientGenerationCompletion(clientsIds: Set[String], replyTo: ActorRef[OperationResult]): Behavior[Command] ={
    replyTo ! ClientGenerator.ClientGenerationCompleted(clientsIds)
    Behaviors.same
  }
}
