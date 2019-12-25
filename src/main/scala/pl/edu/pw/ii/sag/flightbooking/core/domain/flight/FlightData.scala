package pl.edu.pw.ii.sag.flightbooking.core.domain.flight

import java.time.ZonedDateTime

case class FlightData(
                       flightId: String,
                       plane: Plane,
                       startDatetime: ZonedDateTime,
                       endDatetime: ZonedDateTime,
                       source: String,
                       destination: String)
