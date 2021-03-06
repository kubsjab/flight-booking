package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyBehaviourProviderFactory
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyStrategyType.ReplyStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.Plane
import pl.edu.pw.ii.sag.flightbooking.eventsourcing.TaggingAdapter
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration._


object FlightBookingStrategyType extends Enumeration {
  type FlightBookingStrategyType = Value
  val STANDARD, OVERBOOKING = Value
}

case class FlightDetails(flightInfo: FlightInfo, open: Boolean, seatReservations: Map[String, Boolean]) {
  def flightId: String = flightInfo.flightId
  def isFull: Boolean = !seatReservations.view.exists(_._2 == false)
}

case class FlightInfo(flightId: String,
                      airlineId: String,
                      plane: Plane,
                      startDatetime: ZonedDateTime,
                      endDatetime: ZonedDateTime,
                      source: String,
                      destination: String)

object Flight {

  final val TAG = "flight"

  // command
  sealed trait Command extends CborSerializable
  final case class GetFlightDetails(replyTo: ActorRef[FlightDetailsMessage]) extends Command

  final case class Book(flightId: String, seatId: String, customer: Customer, replyTo: ActorRef[BookingOperationResult], requestId: Int) extends Command

  final case class CancelBooking(flightId: String, bookingId: String, replyTo: ActorRef[CancelBookingOperationResult], requestId: Int) extends Command
  final case class CloseFlight(flightId: String, replyTo: ActorRef[CloseFlightOperationResult]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class Booked(seatId: String, booking: Booking) extends Event
  final case class OverBooked(seatId: String, booking: Booking) extends Event
  final case class BookingCancelled(seatId: String, booking: Booking) extends Event
  final case class FlightClosed() extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait BookingOperationResult extends CommandReply
  final case class BookingAccepted(bookingId: String, requestId: Int) extends BookingOperationResult
  final case class BookingRejected(reason: String, requestId: Int) extends BookingOperationResult

  sealed trait CancelBookingOperationResult extends CommandReply
  final case class CancelBookingAccepted(requestId: Int) extends CancelBookingOperationResult
  final case class CancelBookingRejected(reason: String, requestId: Int) extends CancelBookingOperationResult

  sealed trait CloseFlightOperationResult extends CommandReply
  final case class CloseFlightAccepted() extends CloseFlightOperationResult
  final case class CloseFlightRejected(reason: String) extends CloseFlightOperationResult

  final case class FlightDetailsMessage(flightDetails: FlightDetails) extends CommandReply

  //state
  sealed trait State extends CborSerializable {
    val flightInfo: FlightInfo
    val seatReservations: Map[String, Option[Booking]]

    def applyEvent(event: Event): State

    def isBooked(seatId: String): Boolean = seatReservations.getOrElse(seatId, None).isDefined

    def isFlightIdValid(flightId: String): Boolean = flightInfo.flightId == flightId

    def getSeatEntryByBookingId(bookingId: String): Option[(String, Booking)] = {
      seatReservations.view
        .find((seatEntry: (String, Option[Booking])) => seatEntry._2.isDefined && seatEntry._2.get.bookingId == bookingId)
        .map((seatEntry: (String, Option[Booking])) => seatEntry._1 -> seatEntry._2.get)
    }

  }

  def buildId(customId: String): String = s"$TAG-$customId"

  case class OpenedFlight(flightInfo: FlightInfo, seatReservations: Map[String, Option[Booking]]) extends State {
    override def applyEvent(event: Event): State = {
      event match {
        case Booked(seatId, reservation) => copy(flightInfo, seatReservations.updated(seatId, Some(reservation)))
        case OverBooked(seatId, reservation) => copy(flightInfo, seatReservations.updated(seatId, Some(reservation)))
        case BookingCancelled(seatId, _) => copy(flightInfo, seatReservations - seatId)
        case FlightClosed() => ClosedFlight(flightInfo, seatReservations)
      }
    }
  }

  case class ClosedFlight(flightInfo: FlightInfo, seatReservations: Map[String, Option[Booking]]) extends State {
    override def applyEvent(event: Event): State = {
      throw new IllegalStateException(s"Unexpected event [$event] in state [ClosedFlight]")
    }
  }

  def apply(flightInfo: FlightInfo, flightBookingStrategyType: FlightBookingStrategyType, replyStrategyType: ReplyStrategyType): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.supervise(
        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(flightInfo.flightId),
          emptyState = OpenedFlight(flightInfo, flightInfo.plane.seats.map(seat => seat.id -> None).toMap),
          commandHandler = commandHandler(context, flightBookingStrategyType, replyStrategyType),
          eventHandler = eventHandler)
          .withTagger(taggingAdapter)
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(minBackoff = 2.seconds, maxBackoff = 30.seconds, randomFactor = 0.1))
      ).onFailure[Exception](SupervisorStrategy.restart)
    }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    state.applyEvent(event)
  }

  private val taggingAdapter: Event => Set[String] = event => new TaggingAdapter[Event]().tag(event)

  private def commandHandler(context: ActorContext[Command], flightBookingStrategyType: FlightBookingStrategyType, replyStrategyType: ReplyStrategyType): (State, Command) => Effect[Event, State] = {
    val behaviourProvider = ReplyBehaviourProviderFactory.create(context, replyStrategyType)
    flightBookingStrategyType match {
      case FlightBookingStrategyType.STANDARD => StandardFlightBookingStrategy(behaviourProvider).commandHandler(context)
      case FlightBookingStrategyType.OVERBOOKING => OverbookingFlightBookingStrategy(behaviourProvider).commandHandler(context)
    }
  }

}
