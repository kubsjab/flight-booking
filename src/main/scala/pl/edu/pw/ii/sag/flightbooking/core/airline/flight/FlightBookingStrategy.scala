package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._

abstract class FlightBookingStrategy {

  def commandHandler(): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      state match {
        case openState@OpenedFlight(_, _) =>
          cmd match {
            case c: GetFlightDetails => getFlightDetails(state, c)
            case c: Book => bookFlight(openState, c)
            case c: CancelBooking => cancelBooking(openState, c)
            case c: CloseFlight => closeFlight(openState, c)
            case _ => Effect.unhandled.thenNoReply()
          }

        case ClosedFlight(_, _) =>
          cmd match {
            case c: GetFlightDetails => getFlightDetails(state, c)
            case c: Book => Effect.reply(c.replyTo)(Rejected("Can't book a seat to an already closed flight"))
            case c: CancelBooking => Effect.reply(c.replyTo)(Rejected("Can't cancel booking of a seat from an already closed flight"))
            case _: CloseFlight => Effect.unhandled.thenNoReply()
          }
      }
  }

  protected def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State]

  protected def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State]

  protected def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State]

  protected def getFlightDetails(flightState: State, cmd: GetFlightDetails): ReplyEffect[Event, State] = {
    Effect.reply(cmd.replyTo)(
      FlightDetailsMessage(
        FlightDetails(
          flightState.isInstanceOf[OpenedFlight],
          flightState.flightInfo,
          flightState.seatReservations.map { case (k, v) => (k, v.isDefined) }
        )
      )
    )
  }

}
