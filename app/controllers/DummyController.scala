package controllers

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.{Action, Controller}
import scala.concurrent.ExecutionContext
import services.Uptime

@Singleton
class DummyController @Inject() (uptime: Uptime)(implicit val ec: ExecutionContext, val conf: Configuration)
		extends Controller with ApiActionBuilder {
	def nyi0() = NotYetImplemented
	def nyi1(a: String) = NotYetImplemented
	def nyi2(a: String, b: String) = NotYetImplemented

	def undefined(path: String) = Action { NotFound('UNDEFINED_ACTION) }

	def status = ApiAction { req =>
		Ok(Json.obj(
			"server" -> "Eventail API v1",
			"version" -> 1,
			"revision" -> JsNull,
			"start" -> uptime.start.toString,
			"uptime" -> uptime.now,
			"user" -> req.userOpt.map(_.mail)
		))
	}
}
