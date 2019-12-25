package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._

object StandardFlightBookingStrategy {

  def apply(): StandardFlightBookingStrategy = new StandardFlightBookingStrategy()

}

class StandardFlightBookingStrategy extends FlightBookingStrategy {

  override protected def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
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

  override protected def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
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

  override protected def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
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
