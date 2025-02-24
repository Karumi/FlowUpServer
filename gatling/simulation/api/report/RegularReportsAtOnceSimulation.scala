package api.report

import api.report.flowupapi._
import io.gatling.core.Predef._

/**
  * Stress simulation based on some users collecting data for some time and sending it to our servers when a WiFi
  * connection is available. The report data sent to our server contains data associated to one user using the app
  * during some hours and opening two different screens during this time.
  */
class RegularReportsAtOnceSimulation extends Simulation {

  setUp(
    Report.oneUserUsingTheAppTwoTimesPerHourForSomeHours(1).inject(atOnceUsers(1)).protocols(httpConf),
    Report.oneUserUsingTheAppTwoTimesPerHourForSomeHours(2).inject(atOnceUsers(1)).protocols(httpConf),
    Report.oneUserUsingTheAppTwoTimesPerHourForSomeHours(3).inject(atOnceUsers(1)).protocols(httpConf)
  ).assertions(
    global.responseTime.max.lessThan(200),
    global.successfulRequests.percent.is(100)
  )

}
