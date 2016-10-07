package controllers

import play.api.Configuration
import play.api.http.Writeable
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import utils.Implicits.futureWrapper
import utils.{Crypto, DateTime}

/**
  * Mixin trait for API controllers.
  * The controller must be injected with conf and an execution context.
  */
trait ApiActionBuilder extends Controller {
	implicit val conf: Configuration
	implicit val ec: ExecutionContext

	/**
	  * Serializes Throwable instance into JSON objects.
	  * A null Throwable is serialized to JsNull.
	  */
	private[this] def serializeThrowable(t: Throwable, seen: Set[Throwable] = Set()): JsValue = {
		if (t == null || seen.contains(t)) JsNull
		else Json.obj(
			"class" -> t.getClass.getName,
			"message" -> t.getMessage,
			"trace" -> Try { t.getStackTrace.map(_.toString): JsValueWrapper }.getOrElse(Json.arr()),
			"cause" -> serializeThrowable(t, seen + t)
		)
	}

	implicit val throwableWrites: Writes[Throwable] = Writes[Throwable](serializeThrowable(_))

	/** Error message JS object */
	val writeSymbol: (Symbol => JsObject) = sym => Json.obj("err" -> sym.name)

	implicit val errorSymbol = Writeable[Symbol](
		writeSymbol.andThen(implicitly[Writeable[JsValue]].transform),
		Some("application/json")
	)

	implicit class WithCause(val sym: Symbol) {
		def withCause(t: Throwable): JsObject = writeSymbol(sym) + ("cause" -> serializeThrowable(t))
	}

	/** Safely invoke the action constructor and catch potential exceptions */
	private def wrap[R, A](block: R => Future[Result]): R => Future[Result] = (req: R) => {
		try {
			block(req).recover {
				case err: ApiException => err.status.apply(writeSymbol(err.sym))
				case err: Throwable => InternalServerError('UNCAUGHT_EXCEPTION.withCause(err))
			}
		} catch {
			case err: ApiException => err.status.apply(writeSymbol(err.sym))
			case err: Throwable => InternalServerError('UNCAUGHT_EXCEPTION.withCause(err))
		}
	}

	/** An authenticated user action */
	object ApiAction extends ActionBuilder[ApiRequest] {
		/** Builds a new ApiRequest from a verified token */
		def build[A](token: JsObject)(implicit request: Request[A]) = ApiRequest((token \ "user").as[Int], request)

		/** Check that the token expire date is not in the past */
		def checkExpires(token: JsObject): Boolean = (token \ "expires").asOpt[DateTime].exists(_ > DateTime.now)

		/** Returns the token from the X-Auth-Token header or query parameter */
		def token[A](implicit request: Request[A]) = request.headers.get("X-Auth-Token").orElse(request.getQueryString("token"))

		/** Transforms a basic Request to ApiRequest */
		def transform[A](implicit request: Request[A]) = token.flatMap(Crypto.check).filter(checkExpires).map(build[A])

		/** Failure to authenticate */
		def failure = Future.successful(Unauthorized('UNAUTHORIZED))

		/** Invoke the action's block */
		override def invokeBlock[A](request: Request[A], block: ApiRequest[A] => Future[Result]) = {
			println(request.headers.get("Authorization"))
			transform(request).map(wrap(block)).getOrElse(failure)
		}
	}

	/** Safely perform unauthenticated request */
	object UnauthenticatedApiAction extends ActionBuilder[Request] {
		override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]) = {
			println(request.headers.get("Authorization"))
			wrap(block)(request)
		}
	}

	/** API exception */
	case class ApiException(sym: Symbol, status: Status = InternalServerError) extends Exception

	/** Accessor for queryString parameters */
	implicit class QueryStringReader(val req: ApiRequest[_]) {
		private def map[T](key: String)(mapper: String => Option[T]): Option[T] = req.getQueryString(key).flatMap(mapper)
		def getQueryStringAsInt(key: String): Option[Int] = map(key) { s => Try(Integer.parseInt(s)).toOption }
	}

	/** A placeholder for not implemented actions */
	def NotYetImplemented = Action { req => NotImplemented('NOT_YET_IMPLEMENTED) }
}
