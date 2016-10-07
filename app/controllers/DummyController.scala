package controllers

import com.google.inject.{Inject, Singleton}
import models.Users
import play.api.Configuration
import play.api.libs.json.JsArray
import play.api.mvc.{Action, Controller}
import scala.concurrent.ExecutionContext
import utils.SlickAPI._

@Singleton
class DummyController @Inject() (implicit val ec: ExecutionContext, val conf: Configuration)
		extends Controller with ApiActionBuilder {

	def test = ApiAction.async {
		Users.run.map(users => users.map(_.toJson)).map(users => Ok(JsArray(users)))
	}

	def nyi0() = NotYetImplemented
	def nyi1(a: String) = NotYetImplemented
	def nyi2(a: String, b: String) = NotYetImplemented

	def undefined(path: String) = Action { req =>
		NotFound('UNDEFINED_ACTION)
	}
}
