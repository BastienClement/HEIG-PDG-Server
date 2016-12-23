package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import models.{User, Users}
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import services.{CryptoService, UserService}
import utils.DateTime
import utils.Implicits.safeJsReadTyping
import utils.SlickAPI._

@Singleton
class AuthController @Inject() (crypto: CryptoService, users: UserService)
                               (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/**
	  * Generates a new authentication token
	  *
	  * Currently generates one-year tokens, because it will be easier
	  * on the client-side to not handle the re-authentication case.
	  *
	  * @param user the user authenticated by the token
	  */
	private def genToken(user: User, extended: Boolean = false): Result = {
		val duration = if (extended && user.admin) 180.days else 7.days
		val expires = DateTime.now + duration
		val token = Json.obj("user" -> user.id, "expires" -> expires)
		Ok(Json.obj("token" -> crypto.sign(token), "expires" -> expires))
	}

	/**
	  * Request for a new authentication token from credentials
	  */
	def token = ApiAction.async(parse.json) { req =>
		val mail = (req.body \ "mail").to[String]
		val pass = (req.body \ "pass").to[String]

		Users.filter(u => u.mail === mail).map(u => (u, u.pass)).head.filter { case (_, refPass) =>
			crypto.check(pass, refPass)
		}.map { case (u, _) =>
			genToken(u, (req.body \ "extended").asOpt[Boolean].contains(true))
		}.recover { case e =>
			Unauthorized('AUTH_TOKEN_BAD_CREDENTIALS)
		}
	}

	def launch = ApiAction.async { req =>
		val active = req.userOpt match {
			case Some(user) => users.setCurrentActiveDevice(user.id, req.token).replace(true)
			case None => Future.successful(false)
		}
		active.map(a => Ok(Json.obj("active" -> a)))
	}

	def extend = AuthApiAction { req =>
		genToken(req.user)
	}
}
