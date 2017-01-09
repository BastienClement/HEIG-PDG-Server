package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.{ApiActionBuilder, ApiException}
import models.{User, Users}
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import services.{CryptoService, UserService}
import utils.Implicits.safeJsReadTyping
import utils.SlickAPI._
import utils.{BCrypt, DateTime}

/**
  * Authentication controller.
  *
  * Handle everything related to registration, authentication et application life-cycle management.
  *
  * @param crypto an instance of the crypto service
  * @param users  an instance of the users service
  * @param app    a provider for the Play application
  */
@Singleton
class AuthController @Inject() (crypto: CryptoService, users: UserService)
                               (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/**
	  * Generates a new authentication token
	  *
	  * By default, the generated tokens are valid for a 7-days period.
	  *
	  * Administrator users can request an extended token that will stay valid
	  * during during a 180 days period. This is intended for testing purposes
	  * only and should not be used in actual client implementation.
	  *
	  * @param user     the user authenticated by the token
	  * @param extended whether the token should have extended life-time
	  * @param pov      the point of view of the user iteself
	  */
	private def genToken(user: User, extended: Boolean = false)(implicit pov: Users.PointOfView): Result = {
		val duration = if (extended && user.admin) 180.days else 7.days
		val expires = DateTime.now + duration
		val token = Json.obj("user" -> user.id, "expires" -> expires)
		Ok(Json.obj("token" -> crypto.sign(token), "expires" -> expires, "user" -> user))
	}

	/**
	  * Request a new authorization token from user credentials.
	  * Banned users are forbidden from requesting new authorization tokens.
	  */
	def token = ApiAction.async(parse.json) { req =>
		val mail = (req.body \ "mail").to[String]
		val pass = (req.body \ "pass").to[String]

		Users.filter(u => u.mail === mail).map(u => (u, u.pass)).head.filter { case (_, refPass) =>
			crypto.check(pass, refPass)
		}.map { case (u, _) =>
			if (u.banned) throw ApiException('USER_BANNED, Forbidden)
			genToken(u, (req.body \ "extended").asOpt[Boolean].contains(true))(new Users.PointOfView(u))
		}.recover { case e =>
			Unauthorized('AUTH_TOKEN_BAD_CREDENTIALS)
		}
	}

	/**
	  * Registers the launch of a client application.
	  *
	  * This update the currently active device of the user, allowing it to
	  * perform location update, and implicitly disabled every other concurent
	  * client instances.
	  */
	def launch = ApiAction.async { req =>
		val active = req.userOpt match {
			case Some(user) => users.setCurrentActiveDevice(user.id, req.token).replace(true)
			case None => Future.successful(false)
		}
		active.map(a => Ok(Json.obj("active" -> a)))
	}

	/**
	  * Extends the current token, returning a new token for the same user.
	  */
	def extend = AuthApiAction { implicit req =>
		genToken(req.user)
	}

	/**
	  * Creates a new account.
	  */
	def register = ApiAction.async(parse.tolerantJson) { implicit req =>
		if (!req.anon) throw ApiException('ALREADY_REGISTERED, Forbidden)
		val user = (for {
			firstname <- (req.body \ "firstname").validate[String]
			lastname <- (req.body \ "lastname").validate[String]
			username <- (req.body \ "username").validate[String]
			mail <- (req.body \ "mail").validate[String]
			password <- (req.body \ "password").validate[String].map(p => BCrypt.hashpw(p, BCrypt.gensalt()))
		} yield (firstname, lastname, username, mail, password)).getOrElse(Unprocessable)
		val insert = Users.map(u => (u.firstname, u.lastname, u.username, u.mail, u.pass)) += user
		insert.flatMap { _ =>
			Users.filter(u => u.mail === user._4).result.head.map { u =>
				implicit val writes = Users.UserWrites(new Users.PointOfView(u))
				Created(u).withHeaders("location" -> routes.UsersController.user(u.id.toString).url)
			}
		}.transactionally.run.orElse(Conflict('REGISTER_DUPLICATE_MAIL))
	}
}
