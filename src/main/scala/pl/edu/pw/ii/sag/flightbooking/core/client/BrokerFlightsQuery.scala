package pl.edu.pw.ii.sag.flightbooking.core.client

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.broker.Broker
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._

object BrokerFlightsQuery {

  sealed trait Command extends CborSerializable

  final case class AggregatedBrokerFlights(brokerFlights: Map[String, Seq[FlightDetails]]) extends Command

  def getFlights(brokers: Seq[ActorRef[Broker.Command]],
                 replyToWhenCompleted: ActorRef[AggregatedBrokerFlights]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.spawnAnonymous(
        Aggregator[Broker.AirlineFlightDetailsCollection, AggregatedBrokerFlights](
          sendRequests = { replyTo =>
            brokers.foreach(broker => broker ! Broker.GetAirlineFlights(replyTo))
          },
          expectedReplies = brokers.size,
          replyTo = context.self,
          aggregateReplies = brokerFlights => AggregatedBrokerFlights(
            brokerFlights
              .map(c => c.brokerId -> c.airlineFlights.values.flatten.toSeq)
              .filter(_._2.nonEmpty)
              .toMap),
          timeout = 5.seconds))

      Behaviors.receiveMessage {
        case c: AggregatedBrokerFlights =>
          replyToWhenCompleted ! c
          Behaviors.stopped
      }
    }
}
