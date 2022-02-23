package steward.merge.entities

import io.circe.generic.JsonCodec

@JsonCodec
case class StatusCheckRollup(name: String, status: String, conclusion: String)
//object StatusCheckRollup {
//  def apply(json: SomeJson): StatusCheckRollup = {
//    val name: String = json.name.asString.getOrElse(throw ParseFail("name parsing failed"))
//    val status: String = json.status.asString.getOrElse(throw ParseFail("status parsing failed"))
//    val conclusion: String = json.conclusion.asString.getOrElse(throw ParseFail("conclusion parsing failed"))
//    StatusCheckRollup(name, status, conclusion)
//  }
//}