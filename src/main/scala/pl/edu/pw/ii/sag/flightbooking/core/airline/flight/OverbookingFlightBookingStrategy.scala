package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.ReplyEffect
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyBehaviourProvider

object OverbookingFlightBookingStrategy {

  def apply(replyBehaviourProvider: ReplyBehaviourProvider): OverbookingFlightBookingStrategy = new OverbookingFlightBookingStrategy(replyBehaviourProvider)

}

class OverbookingFlightBookingStrategy(behaviourProvider: ReplyBehaviourProvider) extends FlightBookingStrategy(behaviourProvider) {


  override protected def bookFlight(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      context.log.warn(s"Provided flightId is invalid: [${cmd.flightId}]")
      return behaviourProvider.reply(cmd.replyTo, BookingRejected("Invalid flightId", cmd.requestId))
    }
    val booking = Booking.createBooking(cmd.customer)
    if (flightState.isBooked(cmd.seatId)) {
      context.log.warn(s"Seat [${cmd.seatId}] is already booked." +
        s" Replacing old booking: [${flightState.seatReservations(cmd.seatId).get}] " + s"with new one: [${booking}]")
      behaviourProvider.persistAndReply(OverBooked(cmd.seatId, booking), cmd.replyTo, BookingAccepted(booking.bookingId, cmd.requestId))
    }
    else {
      context.log.info(s"Adding booking: [${booking.bookingId}] for seat: [${cmd.seatId}]]")
      behaviourProvider.persistAndReply(Booked(cmd.seatId, booking), cmd.replyTo, BookingAccepted(booking.bookingId, cmd.requestId))
    }
  }

  override protected def cancelBooking(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      context.log.warn(s"Provided flightId is invalid: [${cmd.flightId}]")
      return behaviourProvider.reply(cmd.replyTo, CancelBookingRejected("Invalid flightId"))
    }
    val bookedEntry = flightState.getSeatEntryByBookingId(cmd.bookingId)
    if (bookedEntry.isEmpty) {
      context.log.warn(s"Failed to cancel booking with id ${cmd.bookingId} as it does not exist")
      behaviourProvider.reply(cmd.replyTo, CancelBookingRejected(s"Booking with id ${cmd.bookingId} does not exist"))
    }
    else {
      context.log.info(s"Cancelling booking with id ${cmd.bookingId}")
      behaviourProvider.persistAndReply(BookingCancelled(bookedEntry.get._1, bookedEntry.get._2), cmd.replyTo, CancelBookingAccepted())
    }
  }

  override protected def closeFlight(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      context.log.warn(s"Provided flightId is invalid: [${cmd.flightId}]")
      behaviourProvider.reply(cmd.replyTo, CloseFlightRejected("Invalid flightId"))
    }
    else {
      context.log.info(s"Closing flight with flightId: [${cmd.flightId}]")
      behaviourProvider.persistAndReply(FlightClosed(), cmd.replyTo, CloseFlightAccepted())
    }
  }

}
