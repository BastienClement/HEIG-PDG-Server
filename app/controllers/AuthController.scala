package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import models.{User, Users}
import play.api.Application
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Controller, Result}
import scala.concurrent.duration._
import scala.language.implicitConversions
import services.Crypto
import utils.DateTime
import utils.Implicits.safeJsReadTyping
import utils.SlickAPI._

@Singleton
class AuthController @Inject() (crypto: Crypto)(val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/**
	  * Generates a new authentication token
	  *
	  * Currently generates one-year tokens, because it will be easier
	  * on the client-side to not handle the re-authentication case.
	  *
	  * @param user the user authenticated by the token
	  */
	private def genToken(user: User): Result = {
		val expires = DateTime.now + 7.days
		val token = Json.obj("user" -> user.id, "expires" -> expires)
		Ok(Json.obj("token" -> crypto.sign(token), "expires" -> expires))
	}

	/**
	  * Request for a new authentication token from credentials
	  */
	def token = ApiAction.async(parse.json) { req =>
		val mail = (req.body \ "mail").to[String]
		val pass = (req.body \ "pass").to[String]

		Users.filter(_.mail === mail).head.filter { u =>
			crypto.check(pass, u.pass)
		}.map { u =>
			genToken(u)
		}.recover { case e =>
			Unauthorized('AUTH_TOKEN_BAD_CREDENTIALS)
		}
	}

	def extend = AuthApiAction { req =>
		genToken(req.user)
	}

	def hash = ApiAction(parse.json) { req =>
		val pass = (req.body \ "pass").to[String]
		Ok(JsString(crypto.hash(pass)))
	}
}
