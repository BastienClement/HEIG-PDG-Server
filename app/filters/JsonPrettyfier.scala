package filters

import akka.stream.Materializer
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import utils.Implicits.futureWrapper

class JsonPrettyfier @Inject()(
		implicit override val mat: Materializer,
		exec: ExecutionContext) extends Filter {

	override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
		if (rh.getQueryString("pretty").isEmpty) f(rh)
		else {
			f(rh).flatMap {
				case json if json.body.contentType.exists(_.contains("json")) =>
					json.body.consumeData.map { body =>
						new Results.Status(json.header.status)(Json.prettyPrint(Json.parse(body.utf8String)))
					}

				case other =>
					println(other.body.contentType)
					other
			}
		}
	}
}
