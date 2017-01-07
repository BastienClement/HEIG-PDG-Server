package controllers.api

import models.User
import play.api.mvc.{Request, WrappedRequest}

/**
  * API request with user data
  *
  * @param user    the user's data
  * @param token   the provided authorization token
  * @param request the original request
  * @tparam A the body type of the request
  */
case class ApiRequest[A](user: User, token: String, request: Request[A]) extends WrappedRequest[A](request) {
	/** Whether the request is anonymous */
	final def anon: Boolean = user == null

	/** An optional instance of user, only available if the request is authenticated */
	final def userOpt: Option[User] = Option(user)
}
