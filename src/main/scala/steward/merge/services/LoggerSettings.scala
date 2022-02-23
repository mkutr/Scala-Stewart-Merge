package steward.merge.services

import zio.logging.{LogFormat, LogLevel, Logging}

object LoggerSettings {
  val LoggerEnv =
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("Steward-Scala-Merge")
}
