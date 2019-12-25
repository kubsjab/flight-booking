package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.domain.booking.BookingRequest
import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.FlightData
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

object Flight {

  // command
  sealed trait Command extends CborSerializable
  final case class Book(booking: BookingRequest, replyTo: ActorRef[Confirmation]) extends Command
  final case class CancelBooking(flightId: String, seatId: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class CloseFlight(flightId: String, replyTo: ActorRef[Confirmation]) extends Command

  // event
  sealed trait Event extends CborSerializable
  final case class Booked(seatId: String, booking: Booking) extends Event
  final case class BookingCancelled(seatId: String) extends Event
  final case class FlightClosed() extends Event

  // reply
  sealed trait Confirmation extends CborSerializable
  final case class Accepted() extends Confirmation
  final case class Rejected(reason: String) extends Confirmation

  //state
  sealed trait State extends CborSerializable {
    val flightInfo: FlightData
    val seatReservations: Map[String, Option[Booking]]

    def applyEvent(event: Event): State

    def isBooked(seatId: String): Boolean = seatReservations.getOrElse(seatId, None).isDefined

    def isFlightIdValid(flightId: String): Boolean = flightInfo.flightId == flightId

  }

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

  def apply(flightInfo: FlightData): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("flight"),
      emptyState = OpenedFlight(flightInfo, flightInfo.plane.seats.map(seat => seat.id -> None).toMap),
      commandHandler = commandHandler(flightInfo),
      eventHandler = eventHandler)

  private val eventHandler: (State, Event) => State = { (state, event) =>
    state.applyEvent(event)
  }

  private def commandHandler(flightInfo: FlightData): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      state match {
        case openState@OpenedFlight(_, _) =>
          cmd match {
            case c: Book => bookFlight(openState, c)
            case c: CancelBooking => cancelBooking(openState, c)
            case c: CloseFlight => closeFlight(openState, c)
            case _ => Effect.unhandled.thenNoReply()
          }

        case ClosedFlight(_, _) =>
          cmd match {
            case c: Book => Effect.reply(c.replyTo)(Rejected("Can't book a seat to an already closed flight"))
            case c: CancelBooking => Effect.reply(c.replyTo)(Rejected("Can't cancel booking of a seat from an already closed flight"))
            case _: CloseFlight => Effect.unhandled.thenNoReply()
          }
      }
  }

  private def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.booking.flightId)) {
      return Effect.reply(cmd.replyTo)(Rejected("Invalid flightId"))
    }
    if (flightState.isBooked(cmd.booking.seatId)) {
      Effect.reply(cmd.replyTo)(Rejected(s"Seat with id ${cmd.booking.seatId} is already booked"))
    }
    else {
      Effect
        .persist(Booked(cmd.booking.seatId, Booking(cmd.booking)))
        .thenReply(cmd.replyTo)(_ => Accepted())
    }
  }

  private def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return Effect.reply(cmd.replyTo)(Rejected("Invalid flightId"))
    }
    if (!flightState.isBooked(cmd.seatId)) {
      Effect.reply(cmd.replyTo)(Rejected(s"Seat with id ${cmd.seatId} is not booked"))
    }
    else {
      Effect
        .persist(BookingCancelled(cmd.seatId))
        .thenReply(cmd.replyTo)(_ => Accepted())
    }
  }

  private def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      Effect.reply(cmd.replyTo)(Rejected("Invalid flightId"))
    }
    else {
      Effect
        .persist(FlightClosed())
        .thenReply(cmd.replyTo)(_ => Accepted())
    }
  }

}
