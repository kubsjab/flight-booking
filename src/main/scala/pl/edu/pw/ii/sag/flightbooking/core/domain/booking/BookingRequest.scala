package pl.edu.pw.ii.sag.flightbooking.core.domain.booking

import java.time.ZonedDateTime

import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer

case class BookingRequest(flightId: String, seatId: String, customer: Customer, requestedDate: ZonedDateTime)

