package utils

object ErrorStrings {
	private val msg = Map[Symbol, String](
		'ADMIN_ACTION_RESTRICTED -> "This action can only be performed by administrator users.",
		'AUTH_TOKEN_BAD_CREDENTIALS -> "This user is unknown or the password is incorrect.",
		'AUTHORIZATION_REQUIRED -> "This endpoint requires a valid authorization token.",
		'DATABASE_ERROR -> "An error occurred during a database operation.",
		'EVENT_NOT_FOUND -> "The requested event does not exist.",
		'EVENT_POI_NOT_FOUND -> "The requested point of interest does not exist.",
		'GENERIC_CLIENT_ERROR -> "Something is wrong with this request.",
		'GENERIC_SERVER_ERROR -> "Something is wrong with the server.",
		'REGISTER_DUPLICATE_MAIL -> "Another user with this e-mail address does already exist.",
		'SELF_ONLY_ACTION -> "This action can only be executed for the user itself.",
		'UNCAUGHT_EXCEPTION -> "An exception occurred during the processing of this request.",
		'UNPROCESSABLE_ENTITY -> "The request does not conform to the expected input for this API action.",
		'USER_BANNED -> "This account has been banned and can no longer issue API requests.",
		'USER_INVALID_UID -> "The provided user id is invalid (must be integer or keyword 'self').",
		'USER_NOT_FOUND -> "The requested user does not exist.",
		'USER_RESTRICTED -> "This account has been temporarily restricted and cannot perform this action."
	)

	def get(err: Symbol): String = msg.getOrElse(err, err.name)
}
