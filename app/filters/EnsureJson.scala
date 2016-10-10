package filters

import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import utils.Implicits.futureWrapper

@Singleton
class EnsureJson @Inject()(
		implicit override val mat: Materializer,
		exec: ExecutionContext) extends Filter {

	override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
		if (rh.getQueryString("html").nonEmpty) f(rh)
		else {
			f(rh).flatMap {
				case html if html.body.contentType.exists(_.startsWith("text/html")) =>
					html.body.consumeData.map { body =>
						Results.InternalServerError(Json.obj(
							"err" -> "HTML_RESULT_CONTENT_TYPE",
							"status" -> html.header.status,
							"body" -> body.utf8String
						))
					}

				case other => other
			}
		}
	}
}
