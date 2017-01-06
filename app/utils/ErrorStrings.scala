package utils

object ErrorStrings {
	private val msg = Map[Symbol, String](
		'AUTH_TOKEN_BAD_CREDENTIALS -> "This user is unknown or the password is incorrect.",
		'EVENT_NOT_FOUND -> "The requested event does not exist.",
		'EVENT_POI_NOT_FOUND -> "The requested point of interest does not exist.",
		'GENERIC_CLIENT_ERROR -> "Something is wrong with this request.",
		'GENERIC_SERVER_ERROR -> "Something is wrong with the server.",
		'UNCAUGHT_EXCEPTION -> "An exception occurred during the processing of this request.",
		'UNPROCESSABLE_ENTITY -> "The request does not conform to the expected input for this API action.",
		'USERS_ACTION_SELF_ONLY -> "This action can only be executed for the user itself."
	)

	def get(err: Symbol): String = msg.getOrElse(err, err.name)
}
