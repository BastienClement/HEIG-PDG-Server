package utils

import org.postgresql.util.{PSQLException, ServerErrorMessage}

object PostgresError {
	def unapply(e: PSQLException): Option[(String, String)] = Some(e.getSQLState, e.getServerErrorMessage.toString)

	class ErrorMatcher[T](state: String)(extractor: ServerErrorMessage => T) {
		def unapply(e: PSQLException): Option[T] = {
			if (e.getSQLState == state) Some(extractor(e.getServerErrorMessage))
			else None
		}
	}

	object UniqueViolation extends ErrorMatcher("23505")(e => e.getConstraint)
}
