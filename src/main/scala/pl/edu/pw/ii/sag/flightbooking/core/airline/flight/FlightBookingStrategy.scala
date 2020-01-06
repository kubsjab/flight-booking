package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyBehaviourProvider

abstract class FlightBookingStrategy(val behaviourProvider: ReplyBehaviourProvider) {

  def commandHandler(context: ActorContext[Flight.Command]): (State, Command) => Effect[Event, State] = {
    (state, cmd) =>
      state match {
        case openState@OpenedFlight(_, _) =>
          cmd match {
            case c: GetFlightDetails => getFlightDetails(context, state, c)
            case c: Book => bookFlight(openState, c)
            case c: CancelBooking => cancelBooking(openState, c)
            case c: CloseFlight => closeFlight(openState, c)
            case _ => behaviourProvider.unhandledWithNoReply()
          }

        case ClosedFlight(_, _) =>
          cmd match {
            case c: GetFlightDetails => getFlightDetails(context, state, c)
            case c: Book => behaviourProvider.reply(c.replyTo, BookingRejected("Can't book a seat to an already closed flight", c.requestId))
            case c: CancelBooking => behaviourProvider.reply(c.replyTo, CancelBookingRejected("Can't cancel booking of a seat from an already closed flight"))
            case _: CloseFlight => behaviourProvider.unhandledWithNoReply()
          }
      }
  }

  protected def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State]

  protected def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State]

  protected def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State]

  protected def getFlightDetails(context: ActorContext[Flight.Command], flightState: State, cmd: GetFlightDetails): ReplyEffect[Event, State] = {
    behaviourProvider.reply(cmd.replyTo,
      FlightDetailsMessage(
        FlightDetails(
          flightState.flightInfo,
          flightState.isInstanceOf[OpenedFlight],
          flightState.seatReservations.map { case (k, v) => (k, v.isDefined) }
        )
      )
    )
  }

}
