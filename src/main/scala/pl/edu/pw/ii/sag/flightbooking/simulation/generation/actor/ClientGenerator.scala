package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.core.client.{Client, ClientData, ClientManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._
import scala.util.Random

object ClientGenerator {

  // command
  sealed trait Command extends CborSerializable
  final case class GenerateStandardClients(count: Int,
                                           airlineIds: Set[String],
                                           minBrokersInClientCount: Int,
                                           maxBrokersInClientCount: Int,
                                           replyTo: ActorRef[OperationResult]) extends Command
  final case class AggregatedClients(clientsIds: Set[String], replyTo: ActorRef[OperationResult]) extends Command

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class ClientGenerationCompleted(clientsIds: Set[String]) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult


  def apply(clientManager: ActorRef[ClientManager.Command]): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardClients(count, clientsIds, minBrokersInClientCount, maxBrokersInAirlinesCount, replyTo) =>
        generateClients(context, clientManager, count, clientsIds, minBrokersInClientCount, maxBrokersInAirlinesCount, replyTo)
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
                              count: Int,
                              brokerIds: Set[String],
                              minBrokersInClientCount: Int,
                              maxBrokersInClientCount: Int,
                              replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] = {
    context.log.info("Generating {} clients", count)
    context.spawnAnonymous(
      Aggregator[ClientManager.OperationResult, AggregatedClients](
        sendRequests = { replyTo =>
          (1 to count).foreach(i => {
            val indices = Random.shuffle(0 to (brokerIds.size)-1).toList
              .take(minBrokersInClientCount + Random.nextInt(maxBrokersInClientCount-minBrokersInClientCount))
            val brokerIdsForClient = indices.map(brokerIds.toList).toSet
            clientManager ! ClientManager.CreateClient(ClientData(Client.buildId(i.toString), s"Client-$i", brokerIdsForClient), replyTo)
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
