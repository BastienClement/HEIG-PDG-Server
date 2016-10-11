package filters

import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.mvc.{Filter, RequestHeader, Result}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Ensures that the WWW-Authenticate header is present on Unauthorized result
  */
@Singleton
class UnauthorizedHeader @Inject()(
		implicit override val mat: Materializer,
		exec: ExecutionContext) extends Filter {

	override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
		f(rh).map {
			case unauthorized if unauthorized.header.status == Status.UNAUTHORIZED =>
				unauthorized.withHeaders("WWW-Authenticate" -> "Token (Eventail)")

			case other => other
		}
	}
}
