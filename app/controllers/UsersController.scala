package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.{ApiActionBuilder, ApiException, ApiRequest}
import models.User
import play.api.Application
import play.api.mvc.Controller
import scala.util.Try
import services.Location
import utils.Implicits.safeJsReadTyping

/**
  * The controller handing user-related operations.
  *
  * @param app the Play application instance
  * @param loc the Location service
  */
@Singleton
class UsersController @Inject() (val app: Provider[Application], val loc: Location)
		extends Controller with ApiActionBuilder {
	/**
	  * Extracts the numeric user ID from the given URI parameter.
	  *
	  * @param uid      the URI parameter
	  * @param selfOnly only allow the "self" keyword and the user's own ID
	  * @param strict   enforce the selfOnly restriction even for administrators
	  * @param req      the request object
	  * @tparam A the body type of the request
	  * @return the corresponding numeric user ID
	  */
	private def userId[A](uid: String, selfOnly: Boolean = false, strict: Boolean = false)
			(implicit req: ApiRequest[A]): Int = {
		uid match {
			case "self" => req.user.id
			case _ =>
				val id = Try(uid.toInt).getOrElse(throw ApiException('USERS_INVALID_UID, UnprocessableEntity))
				if (selfOnly && !req.userOpt.exists { u => u.id == id || (!strict && u.admin) }) {
					throw ApiException('USERS_ACTION_SELF_ONLY, Forbidden)
				} else {
					id
				}
		}
	}

	/**
	  * Executes the action if the given user parameter is the caller itself.
	  * Otherwise, the 'USERS_ACTION_SELF_ONLY error is thrown.
	  *
	  * @param uid    the URI parameter
	  * @param action the action to execute
	  * @param req    the request object
	  * @tparam A the body type of the request
	  * @tparam T the return type of the action
	  * @return the return value of the action
	  */
	private def requireSelf[A, T](uid: String)(action: User => T)(implicit req: ApiRequest[A]): T = {
		userId(uid, selfOnly = true, strict = true)
		action(req.user)
	}

	/**
	  * Returns the list of users matching the given filters.
	  */
	def list = NotYetImplemented

	/**
	  * Returns user information.
	  *
	  * @param user the user id or the keyword "self"
	  */
	def user(user: String) = ApiAction.async { req =>
		NotYetImplemented(req)
	}

	/**
	  * Updates user location.
	  *
	  * @param uid the user id or the keyword "self"
	  */
	def location(uid: String) = AuthApiAction.async(parse.json) { implicit req =>
		requireSelf(uid) { user =>
			val lat = (req.body \ "lat").to[Double]
			val lon = (req.body \ "lon").to[Double]
			loc.updateUser(user, lat, lon)
		}.map { _ => NoContent }
	}
}
