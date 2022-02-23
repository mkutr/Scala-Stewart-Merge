package steward.merge.entities

import io.circe.Json
import io.circe.generic.JsonCodec, io.circe.syntax._
import scala.collection.Seq

@JsonCodec
case class BranchInfo(number: Option[Int], headRefName: String, statusCheckRollup: Seq[StatusCheckRollup], body: Option[String])


//object BranchInfo {
//  def apply(json: Json): BranchInfo = {
//    new BranchInfo(Some(1), "!23,", Seq.empty, None)
//    val prNumJson = json.asObject.getOrElse("number"). //.getOrElse(throw steward.merge.Entities.ParseFail("prNum parsing failed"))
//    val branchNameJson: String = json.headRefName.asString.getOrElse(throw ParseFail("branchName parsing failed"))
//    val statusCheckRollups = json.statusCheckRollup.toSeq.map(StatusCheckRollup(_))
//    val body = json.body.asString //.getOrElse(throw steward.merge.Entities.ParseFail("body parsing failed"))
//    BranchInfo(prNumJson, branchNameJson, statusCheckRollups, body)
//  }
//}
