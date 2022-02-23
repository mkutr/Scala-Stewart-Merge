package steward.merge.services

import steward.merge.entities.BranchInfo
import steward.merge.services.CommandExecutor.CommandExecutorEnv
import steward.merge.services.JsonParser.JsonParserEnv
import zio.blocking.Blocking
import zio.console.Console.Service.live.putStrLn
import zio.logging.{Logging, log}
import zio.process.Command
import zio.{Has, ZIO, ZLayer}

import scala.collection.Seq

object GithubAccess {
  type AccessEnv = CommandExecutorEnv with JsonParserEnv with Blocking with Logging

  type GithubAccessEnv = Has[GithubAccess.Service]

  trait Service {

    def fetchAll: ZIO[AccessEnv, Throwable, Seq[BranchInfo]]

    def fetchAllFailOnNotComplete: ZIO[AccessEnv, Throwable, Seq[BranchInfo]]

    def pushWithUpstream(branch: BranchInfo): ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]

    def checkoutBranch(branch: BranchInfo): ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]

    def commit: ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]

    def merge(branch: BranchInfo): ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]

    def push(branch: BranchInfo): ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]

    def editPR(branchInfo: BranchInfo): ZIO[CommandExecutorEnv with AccessEnv, Throwable, String]
  }

  val live: ZLayer[Any, Nothing, Has[Service]] = ZLayer.succeed(new Service {
    override def fetchAll: ZIO[AccessEnv, Throwable, Seq[BranchInfo]] = {
      val listPRsCmd = Command("gh", "pr", "list", "--search", "'is:open is:pr author:app/github-actions Update'", "--json", "title,body,number,headRefName,statusCheckRollup")
      val ret: ZIO[JsonParserEnv with Logging with CommandExecutorEnv with Blocking, Throwable, List[BranchInfo]] = for {
        retStr <- CommandExecutor.execute(listPRsCmd)
        _ <- log.info(retStr)
        branchInfo <- JsonParser.parseJson(retStr)
        _ <- log.debug(branchInfo.toString())
        //        branchInfo <- ZIO.succeed(jsons.toSeq.map(BranchInfo(_)))
        _ <- putStrLn(retStr)
      } yield branchInfo
      ret //.mapError(log.error()) //orElseFail(new Throwable("Fetching failed")) //catchSome{case err: String => ZIO.fail(Entities.FetchFail)}
    }

    override def fetchAllFailOnNotComplete: ZIO[AccessEnv, Throwable, Seq[BranchInfo]] = {
      val fetched = fetchAll

      val allCompleted: ZIO[AccessEnv, Throwable, Boolean] = fetched.map(r => r.forall(_.statusCheckRollup.forall(_.status == "COMPLETED")))
      val allSuccessful: ZIO[AccessEnv, Throwable, Seq[BranchInfo]] = fetched.map(r => r.filter(_.statusCheckRollup.forall(_.conclusion == "SUCCESS")))
      for {
        allCompleted: Boolean <- allCompleted
        successful: Seq[BranchInfo] <- allSuccessful
        _ <- ZIO.fail(new Throwable("Not all branches completed CI flow")).unless(allCompleted)
      } yield successful
    }

    override def checkoutBranch(branch: BranchInfo) = CommandExecutor.execute(Command("git", "checkout", "-b", branch.headRefName))

    override def pushWithUpstream(branch: BranchInfo) = CommandExecutor.execute(Command("git", "push", branch.headRefName))

    override def commit = CommandExecutor.execute(Command("git", "commit", "--no-edit", "--amend"))

    override def merge(branch: BranchInfo) = CommandExecutor.execute(Command("git", "merge", "-Xignore-all-space", "--no-ff", "--no-edit", branch.headRefName))

    override def push(branch: BranchInfo) = CommandExecutor.execute(Command("git", "push", "--force-with-lease", "origin", branch.headRefName))

    override def editPR(branchInfo: BranchInfo) = CommandExecutor.execute(Command("gh", "pr", "edit", branchInfo.number.get.toString, "--base", branchInfo.headRefName))
  })

  def fetchAll: ZIO[GithubAccessEnv with AccessEnv, Throwable, Seq[BranchInfo]] = {
    ZIO.accessM(hasService => hasService.get.fetchAll)
  }

  def fetchAllFailOnNotComplete: ZIO[GithubAccessEnv with AccessEnv, Throwable, Seq[BranchInfo]] = {
    ZIO.accessM(hasService => hasService.get.fetchAllFailOnNotComplete)
  }

  def checkoutBranch(branch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.checkoutBranch(branch))
  }

  def pushWithUpstream(branch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.pushWithUpstream(branch))
  }

  def commit: ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.commit)
  }

  def merge(branch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.merge(branch))
  }

  def push(branch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.push(branch))
  }

  def editPR(branch: BranchInfo): ZIO[GithubAccessEnv with AccessEnv, Throwable, String] = {
    ZIO.accessM(hasService => hasService.get.editPR(branch))
  }
}
