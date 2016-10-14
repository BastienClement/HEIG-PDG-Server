package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.{Action, Controller}
import services.Uptime

@Singleton
class DummyController @Inject() (uptime: Uptime)(val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	def nyi0() = NotYetImplemented
	def nyi1(a: String) = NotYetImplemented
	def nyi2(a: String, b: String) = NotYetImplemented

	def undefined(path: String) = Action { req =>
		NotFound(Json.obj(
			"error" -> "UNDEFINED_ACTION",
			"message" -> "The resource is undefined or the method is unavailable.",
			"method" -> req.method,
			"resource" -> req.path,
			"querystring" -> req.rawQueryString
		))
	}

	def status = ApiAction { req =>
		Ok(Json.obj(
			"server" -> "Eventail API v1",
			"version" -> 1,
			"revision" -> JsNull,
			"mode" -> app.get.mode.toString,
			"start" -> uptime.start.toString,
			"uptime" -> uptime.now,
			"user" -> req.userOpt.map(_.mail)
		))
	}
}
