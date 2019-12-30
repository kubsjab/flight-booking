package pl.edu.pw.ii.sag.flightbooking.simulation.generation.data

import scala.concurrent.duration.FiniteDuration

case class FlightGenScheduledTaskData(scheduleEnabled: Boolean, minCount: Int, maxCount: Int, delay: FiniteDuration)