package controllers.api

import com.google.inject.Provider
import gql.Ctx
import models.{User, Users}
import play.api.Application
import play.api.cache.CacheApi
import play.api.http.Writeable
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try
import services.Crypto
import utils.Implicits.futureWrapper
import utils.{DateTime, ErrorStrings}
import utils.SlickAPI._

/**
  * Mixin trait for API controllers.
  * The controller must be injected with conf and an execution context.
  */
trait ApiActionBuilder extends Controller {
	val app: Provider[Application]

	implicit lazy val ec = app.get.injector.instanceOf[ExecutionContext]
	implicit lazy val crypto = app.get.injector.instanceOf[Crypto]
	implicit lazy val cache = app.get.injector.instanceOf[CacheApi]

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
	val writeSymbol: (Symbol => JsObject) = sym => Json.obj(
		"error" -> sym.name,
		"message" -> ErrorStrings.get(sym)
	)

	implicit val errorSymbol = Writeable[Symbol](
		writeSymbol.andThen(implicitly[Writeable[JsValue]].transform),
		Some("application/json")
	)

	implicit class WithOpsJsObject(val obj: JsObject) {
		def withCause(t: Throwable): JsObject = obj + ("cause" -> serializeThrowable(t))
		def withDetails[D](details: D)(implicit wjs: Writes[D]): JsObject = obj + ("details" -> wjs.writes(details))
	}

	implicit class WithOpsSymbol(val sym: Symbol) {
		def withCause(t: Throwable): JsObject = writeSymbol(sym) withCause t
		def withDetails[D: Writes](details: D): JsObject = writeSymbol(sym) withDetails details
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

	/** An API action */
	object ApiAction extends ActionBuilder[ApiRequest] {
		/** Returns the token from the Authorization header or query string parameter */
		private def token[A](implicit request: Request[A]): Option[String] = {
			request.getQueryString("token").orElse {
				request.headers.get("Authorization").collect {
					case auth if auth.toLowerCase.startsWith("token ") => auth.substring(6).trim
				}
			}.orElse {
				request.headers.get("X-Auth-Token")
			}
		}

		/** Decodes the token string and validates expiration date */
		private def decode(token: String): Option[JsObject] = {
			for {
				obj <- crypto.check(token)
				if (obj \ "expires").asOpt[DateTime].exists(_ > DateTime.now)
			} yield obj
		}

		/** Fetches the user from the request token, if available */
		private def user[A](implicit request: Request[A]): Future[Option[User]] = {
			token.flatMap(decode) match {
				case Some(tok) => Users.findById((tok \ "user").as[Int]).headOption
				case None => Future.successful(None)
			}
		}

		/** Transforms a basic Request to ApiRequest */
		def transform[A](implicit request: Request[A]): Future[ApiRequest[A]] = request match {
			case apiRequest: ApiRequest[A] => apiRequest
			case other => for (u <- user) yield ApiRequest(u.orNull, other)
		}

		/** Invoke the action's block */
		override def invokeBlock[A](request: Request[A], block: ApiRequest[A] => Future[Result]): Future[Result] = {
			transform(request).flatMap(wrap(block))
		}
	}

	/** An authenticated API action */
	object AuthApiAction extends ActionBuilder[ApiRequest] {
		override def invokeBlock[A](request: Request[A], block: (ApiRequest[A]) => Future[Result]): Future[Result] = {
			ApiAction.transform(request).flatMap { req =>
				if (req.anon) Unauthorized('AUTHORIZATION_REQUIRED)
				else wrap(block)(req)
			}
		}
	}

	/** Accessor for queryString parameters */
	implicit class QueryStringReaderOps(private val req: Request[_]) {
		def getQueryStringAs[T](key: String)(implicit qsrm: QueryStringReader[T]): Option[T] = {
			req.getQueryString(key).flatMap(qsrm.mapper)
		}

		def getQueryStringAsInt(key: String): Option[Int] = getQueryStringAs[Int](key)
		def getQueryStringAsDouble(key: String): Option[Double] = getQueryStringAs[Double](key)
	}

	case class QueryStringReader[T](mapper: String => Option[T])
	object QueryStringReader {
		private def unsafeWrapper[T](mapper: String => T) = QueryStringReader[T](s => Try(mapper(s)).toOption)

		implicit val stringMapper: QueryStringReader[String] = QueryStringReader(Some.apply)
		implicit val intMapper: QueryStringReader[Int] = unsafeWrapper(_.toInt)
		implicit val doubleMapper: QueryStringReader[Double] = unsafeWrapper(_.toDouble)
	}

	private def Unprocessable: Nothing = throw ApiException('UNPROCESSABLE_ENTITY, UnprocessableEntity)

	def param[T: Reads : QueryStringReader](name: String, default: => T = Unprocessable)(implicit req: Request[AnyContent]): T = {
		req.body.asJson.flatMap(b => (b \ name).asOpt[T]).orElse { req.getQueryStringAs[T](name) }.getOrElse(default)
	}

	/** A placeholder for not implemented actions */
	def NotYetImplemented = Action { req => NotImplemented('NOT_YET_IMPLEMENTED) }

	/** Automatic GraphQL Ctx construction */
	implicit def implicitGraphQLContext(implicit req: ApiRequest[_]): Ctx = Ctx(req.userOpt)
}
