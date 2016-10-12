package utils

object ErrorStrings {
	private val msg = Map[Symbol, String](

	)

	def get(err: Symbol): String = msg.getOrElse(err, err.name)
}
