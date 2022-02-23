package steward.merge.services

import zio.blocking.Blocking
import zio.logging.{Logging, log}
import zio.process.{Command, CommandError}
import zio.{Has, ULayer, ZIO, ZLayer}

import scala.collection.Seq

object CommandExecutor {
  type CommandExecutorEnv = Has[CommandExecutor.Service]

  trait Service {
    def execute(command: Command): ZIO[Blocking with Logging, CommandError, String]

    def execute(commands: Seq[Command]): ZIO[Blocking with Logging, CommandError, Seq[String]]
  }

  val live: ULayer[Has[Service]] = ZLayer.succeed(new Service {
    override def execute(command: Command): ZIO[Blocking with Logging, CommandError, String] = {
      for {
        rts <- command.string
        _ <- log.debug(f"executed: ${command.toString}")
        _ <- log.debug(f"output: $rts")
      } yield rts
    }

    override def execute(commands: Seq[Command]): ZIO[Blocking with Logging, CommandError, Seq[String]] =
      ZIO.foreach(commands)(execute)
  })

  def execute(command: Command): ZIO[CommandExecutorEnv with Blocking with Logging, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.execute(command))
  }

  def execute(commands: Seq[Command]): ZIO[CommandExecutorEnv with Blocking with Logging, Throwable, Seq[String]] = {
    ZIO.accessM(hasService => ZIO.foreach(commands)(hasService.get.execute))
  }
}
