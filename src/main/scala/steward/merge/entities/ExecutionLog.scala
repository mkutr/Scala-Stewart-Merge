package steward.merge.entities

trait ExecutionLog

case class ExecutionList(list: List[String]) extends ExecutionLog

case object EmptyLog extends ExecutionLog