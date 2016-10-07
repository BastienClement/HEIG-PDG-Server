package controllers

import com.google.inject.{Inject, Singleton}
import models.{User, Users}
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.mvc.Controller
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import utils.SlickAPI._
import utils.{Crypto, DateTime}

@Singleton
class AuthController @Inject() (implicit val ec: ExecutionContext, val conf: Configuration)
		extends Controller with ApiActionBuilder {

	/**
	  * Generates a new authentication token
	  *
	  * Currently generates one-year tokens, because it will be easier
	  * on the client-side to not handle the re-authentication case.
	  *
	  * @param user the user authenticated by the token
	  */
	private def genToken(implicit user: User): String = {
		val token = Json.obj("user" -> user.id, "expires" -> (DateTime.now + 7.days))
		Crypto.sign(token)
	}

	/**
	  * Request for a new authentication token from credentials
	  */
	def token = ApiAction.async(parse.json) { req =>
		val mail = (req.body \ "mail").as[String]
		val pass = (req.body \ "pass").as[String]

		Users.filter(_.mail === mail).head.filter { u =>
			Crypto.check(pass, u.pass)
		}.map { u =>
			Ok(Json.obj("token" -> genToken(u)))
		}.recover { case e =>
			Unauthorized('TOKEN_BAD_CREDENTIALS)
		}
	}

	def extend = UserApiAction { req =>
		Ok(Json.obj("token" -> genToken(req.user)))
	}

	def hash = ApiAction(parse.json) { req =>
		Ok(JsString(Crypto.hash((req.body \ "pass").as[String])))
	}
}
