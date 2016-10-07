package controllers

import play.api.mvc.{Request, WrappedRequest}

/**
  * API request with user ID
  *
  * @param user    the user's ID
  * @param request the original request
  * @tparam A the body type of the request
  */
case class ApiRequest[A](user: Int, request: Request[A]) extends WrappedRequest[A](request)
