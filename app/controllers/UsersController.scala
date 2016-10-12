package controllers

import com.google.inject.{Inject, Provider, Singleton}
import play.api.Application
import play.api.mvc.Controller

@Singleton
class UsersController @Inject() (val app: Provider[Application])
		extends Controller with ApiActionBuilder {

	def user(id: Int) = ApiAction.async { req =>
		NotYetImplemented(req)
	}

	def self = UserApiAction.async { req => user(req.user.id)(req) }
}
