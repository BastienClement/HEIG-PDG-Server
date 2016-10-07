package controllers

import com.google.inject.{Inject, Singleton}
import models.Users
import play.api.Configuration
import play.api.libs.json.JsArray
import play.api.mvc.Controller
import scala.concurrent.ExecutionContext
import utils.SlickAPI._

@Singleton
class DummyController @Inject() (implicit val ec: ExecutionContext, val conf: Configuration)
		extends Controller with ApiActionBuilder {

	def test = UnauthenticatedApiAction.async {
		Users.run.map(users => users.map(_.toJson)).map(users => Ok(JsArray(users)))
	}
}
