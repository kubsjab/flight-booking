package pl.edu.pw.ii.sag.flightbooking.core.client.booking

import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightInfo
import pl.edu.pw.ii.sag.flightbooking.core.client.booking.BookingStatus.BookingStatus

object BookingStatus extends Enumeration {
  type BookingStatus = Value
  val CONFIRMED, CANCELLED, REJECTED, NEW = Value
}

object BookingData {
  def apply(id: Int, brokerId: String, flightInfo: FlightInfo, seat: String): BookingData = new BookingData(id, brokerId, flightInfo, seat, BookingStatus.NEW, null)
}

case class BookingData(
                        id: Int,
                        brokerId: String,
                        flightInfo: FlightInfo,
                        seat: String,
                        bookingStatus: BookingStatus,
                        additionalData: String
                      ) {
  def accepted(bookingId: String): BookingData = {
    BookingData(id, brokerId, flightInfo, seat, BookingStatus.CONFIRMED, bookingId)
  }

  def rejected(reason: String): BookingData = {
    BookingData(id, brokerId, flightInfo, seat, BookingStatus.REJECTED, reason)
  }

  def cancelled(): BookingData = {
    BookingData(id, brokerId, flightInfo, seat, BookingStatus.CANCELLED, null)
  }
}
