package steward.merge.entities

trait FetchFail extends Throwable

case class FetchFailNoElement(string: String) extends NoSuchElementException

case class ParseFail(string: String) extends InstantiationException
