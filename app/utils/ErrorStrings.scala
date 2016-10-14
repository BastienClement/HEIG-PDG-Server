package utils

object ErrorStrings {
	private val msg = Map[Symbol, String](
		'AUTH_TOKEN_BAD_CREDENTIALS -> "This user is unknown or the password is incorrect.",
		'UNPROCESSABLE_ENTITY -> "The request does not conform to the expected input for this API action."
	)

	def get(err: Symbol): String = msg.getOrElse(err, err.name)
}
