package steward.merge

import steward.merge.entities.{BranchInfo, ExecutionList, FetchFailNoElement}
import steward.merge.services.{CommandExecutor, GithubAccess, JsonParser, LoggerSettings}
import steward.merge.Utilities.mapN
import steward.merge.services.CommandExecutor.CommandExecutorEnv
import steward.merge.services.GithubAccess._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.{Logging, log}
import zio.process.Command
import zio.{ExitCode, Schedule, URIO, ZIO, duration}

import java.time.LocalDate.now
import scala.collection.Seq

object Main extends zio.App {
  def getNotEmptyBranches(branchInfos: Seq[BranchInfo]): Seq[BranchInfo] = {
    branchInfos.collect {
      case b@BranchInfo(pr, _, _, _) if pr.nonEmpty => b
    }
  }

  def step1ProcessPRs: ZIO[GithubAccessEnv with AccessEnv, Throwable, Seq[BranchInfo]] = {
    GithubAccess.fetchAll
  }

  def step2AssignSelf(branchInfos: Seq[BranchInfo]): ZIO[CommandExecutorEnv with Blocking with Logging, Throwable, Unit] = {
    val assignMeCmds = branchInfos.flatMap(_.number).map(number => Command("gh", "pr", "edit", number.toString, "--add-assignee", "@me"))
    for {
      _ <- CommandExecutor.execute(assignMeCmds)
      _ <- ZIO.when(assignMeCmds.isEmpty)(ZIO.fail(FetchFailNoElement("No PR id")))
    } yield ()
  }

  def step3GenerateConfigEntries(branchInfos: Seq[BranchInfo]): ZIO[Console with Logging, Nothing, Seq[Unit]] = {
    val gitMachete: Seq[String] = branchInfos.collect {
      case b@BranchInfo(pr, _, _, _) if pr.nonEmpty => b
    }.map(branchInfo => f"        ${branchInfo.headRefName} PR #${branchInfo.number.get}")
    ZIO.foreach(gitMachete)(log.info(_))
  }

  def step4triggerCIPipeline(branchInfos: Seq[BranchInfo]): ZIO[GithubAccessEnv with AccessEnv with Clock, Throwable, Seq[Any]] = {
    def triggerBranch(branchInfo: BranchInfo) = {
      mapN(log.info(f"[ PR #${branchInfo.number.get} - ${branchInfo.headRefName} needs triggering ]"),
        checkoutBranch(branchInfo),
        commit,
        push(branchInfo),
        ZIO.sleep(15.seconds))((_, b, c, d, _) => ExecutionList(List(b, c, d)))
    }

    for {
      r <- ZIO.foreach(branchInfos) {
        case branchInfo: BranchInfo if branchInfo.statusCheckRollup.isEmpty && branchInfo.number.nonEmpty => triggerBranch(branchInfo)
        case branchInfo: BranchInfo => log.info(f"[ PR #${branchInfo.number.get} - ${branchInfo.headRefName} has CI status ]")
      }
    } yield r
  }

  def step5createTargetBranchAndModify(branchInfos: Seq[BranchInfo], batchBranch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, Unit] = {
    for {
      _: Seq[Unit] <- ZIO.foreach(branchInfos)(branchInfo => log.info(f"Editing PR #${branchInfo.number.get.toString} - ${branchInfo.headRefName}"))
      _ <- ZIO.foreach_(branchInfos)(branchInfo => editPR(branchInfo))
    } yield ()
  }

  def step6collectPRsSucceededAndPush: ZIO[GithubAccessEnv with AccessEnv with Clock, Throwable, Seq[BranchInfo]] = {
    def collectPRsSucceeded: ZIO[GithubAccessEnv with AccessEnv with Clock, Throwable, Seq[BranchInfo]] = for {
      successfulBranches: Seq[BranchInfo] <- GithubAccess.fetchAllFailOnNotComplete.retry(Schedule.spaced(duration.Duration.fromMillis(30000)))
      _ <- log.info("########[  PRs TO BE MERGED  ]########")
      _ <- ZIO.foreach_(successfulBranches.map(_.headRefName))(log.info(_))
    } yield (successfulBranches)

    def mergeSuccessful(successfulBranches: ZIO[GithubAccessEnv with AccessEnv with Clock, Throwable, Seq[BranchInfo]]) =
      for {
        branches: Seq[BranchInfo] <- successfulBranches
        _ <- ZIO.foreach_(branches)(merge)
      } yield branches

    mergeSuccessful(collectPRsSucceeded)
  }

  def step7createMergePR(successfulPRs: Seq[BranchInfo], batchBranch: BranchInfo): ZIO[Logging, Nothing, Unit] = {
    def makePRContent(pr: BranchInfo) = {
      val head = f"PR #${pr.number.toString} - ${pr.headRefName}"
      val body = pr.body.getOrElse("")
      val bodyCut = body.substring(0, body.indexOf("I'll automatically update this PR"))
      f"$head\n$bodyCut\n"
    }

    val branchesContents: String = successfulPRs.map(makePRContent).reduce(_ ++ _)
    val title = batchBranch.headRefName
    val assignee = "self"
    val allContents = f"title: $title\nassignee: $assignee\nbody content generated below:\n$branchesContents"
    log.info("Make a PR with contents:\n" + allContents)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val headRefName = "update/batch-" + now().toString
    val batchBranch = BranchInfo(None, headRefName, Seq.empty, None)

    val ret = for {
      _ <- checkoutBranch(batchBranch)
      _ <- pushWithUpstream(batchBranch)
      branchInfos <- step1ProcessPRs
      branchInfosNotEmpty = getNotEmptyBranches(branchInfos)
      _ <- step2AssignSelf(branchInfos)
      _ <- step3GenerateConfigEntries(branchInfosNotEmpty)
      _ <- step4triggerCIPipeline(branchInfosNotEmpty)
      _ <- step5createTargetBranchAndModify(branchInfosNotEmpty, batchBranch)
      _ <- checkoutBranch(batchBranch)
      succeededBranchInfos <- step6collectPRsSucceededAndPush
      _ <- push(batchBranch)
      _ <- step7createMergePR(succeededBranchInfos, batchBranch)


    } yield ()
    ret.provideCustomLayer(JsonParser.live ++ CommandExecutor.live ++ GithubAccess.live ++ LoggerSettings.LoggerEnv).exitCode
  }
}
