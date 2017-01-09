package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import services.UptimeService

/**
  * A dummy controller used as placeholder for not yet implemented features and
  * basic server status information API.
  *
  * @param uptime an instance of the uptime service
  * @param app    a provider for the Play-application instance
  */
@Singleton
class DummyController @Inject() (uptime: UptimeService)
                                (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/** A placeholder for an action with 0 parameter */
	def nyi0 = NotYetImplemented

	/** A placeholder for an action with 1 parameter */
	def nyi1(a: String) = NotYetImplemented

	/** A placeholder for an action with 2 parameters */
	def nyi2(a: String, b: String) = NotYetImplemented

	/** The global catch-all method handling requests to undefined endpoints */
	def undefined(path: String) = Action { req =>
		NotFound(Json.obj(
			"error" -> "UNDEFINED_ACTION",
			"message" -> "The resource is undefined or the method is unavailable.",
			"method" -> req.method,
			"resource" -> req.path,
			"querystring" -> req.rawQueryString
		))
	}

	/** Returns server information data */
	def status = ApiAction { req =>
		Ok(Json.obj(
			"server" -> "Eventail API v1",
			"version" -> 1,
			"mode" -> app.get.mode.toString,
			"start" -> uptime.start.toString,
			"uptime" -> uptime.now,
			"user" -> req.userOpt.map(_.mail)
		))
	}
}
