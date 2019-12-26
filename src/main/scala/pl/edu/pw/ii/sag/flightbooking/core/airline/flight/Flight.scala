package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightBookingStrategyType.FlightBookingStrategyType
import pl.edu.pw.ii.sag.flightbooking.core.domain.booking.BookingRequest
import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.Plane
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

object FlightBookingStrategyType extends Enumeration {
    type FlightBookingStrategyType = Value
    val STANDARD, OVERBOOKING = Value
}

case class FlightActorWrapper(flightData: FlightData, flightActor: ActorRef[Flight.Command])

case class FlightDetails(open: Boolean, flightData: FlightData, seatReservations: Map[String, Boolean])

case class FlightData(flightId: String,
                      plane: Plane,
                      startDatetime: ZonedDateTime,
                      endDatetime: ZonedDateTime,
                      source: String,
                      destination: String)

object Flight {

  // command
  sealed trait Command extends CborSerializable
  final case class GetFlightDetails(replyTo: ActorRef[FlightDetailsMessage]) extends Command
  final case class Book(booking: BookingRequest, replyTo: ActorRef[OperationResult]) extends Command
  final case class CancelBooking(flightId: String, seatId: String, replyTo: ActorRef[OperationResult]) extends Command
  final case class CloseFlight(flightId: String, replyTo: ActorRef[OperationResult]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class Booked(seatId: String, booking: Booking) extends Event
  final case class BookingCancelled(seatId: String) extends Event
  final case class FlightClosed() extends Event

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class Accepted() extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class FlightDetailsMessage(flightDetails: FlightDetails) extends CommandReply

  //state
  sealed trait State extends CborSerializable {
    val flightInfo: FlightData
    val seatReservations: Map[String, Option[Booking]]

    def applyEvent(event: Event): State

    def isBooked(seatId: String): Boolean = seatReservations.getOrElse(seatId, None).isDefined

    def isFlightIdValid(flightId: String): Boolean = flightInfo.flightId == flightId

  }

  def buildId(customId: String): String = s"flight-$customId"

  case class OpenedFlight(flightInfo: FlightData, seatReservations: Map[String, Option[Booking]]) extends State {
    override def applyEvent(event: Event): State = {
      event match {
        case Booked(seatId, reservation) => copy(flightInfo, seatReservations.updated(seatId, Some(reservation)))
        case BookingCancelled(seatId) => copy(flightInfo, seatReservations - seatId)
        case FlightClosed() => ClosedFlight(flightInfo, seatReservations)
      }
    }
  }

  case class ClosedFlight(flightInfo: FlightData, seatReservations: Map[String, Option[Booking]]) extends State {
    override def applyEvent(event: Event): State = {
      throw new IllegalStateException(s"Unexpected event [$event] in state [ClosedFlight]")
    }
  }

  def apply(flightData: FlightData, flightBookingStrategyType: FlightBookingStrategyType): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(flightData.flightId),
      emptyState = OpenedFlight(flightData, flightData.plane.seats.map(seat => seat.id -> None).toMap),
      commandHandler = commandHandler(flightBookingStrategyType),
      eventHandler = eventHandler)

  private val eventHandler: (State, Event) => State = { (state, event) =>
    state.applyEvent(event)
  }

  private def commandHandler(flightBookingStrategyType: FlightBookingStrategyType): (State, Command) => Effect[Event, State] = {
    flightBookingStrategyType match {
      case FlightBookingStrategyType.STANDARD => StandardFlightBookingStrategy().commandHandler()
      case FlightBookingStrategyType.OVERBOOKING => ???
    }
  }



}
