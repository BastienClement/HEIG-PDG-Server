import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, JsValue, Json}
import sangria.ast.Document
import sangria.marshalling.playJson._

package object gql {
	implicit class QueryExecutor(private val query: Document) extends AnyVal {
		def execute(args: (String, JsValueWrapper)*)(implicit gql: GraphQL, ctx: Ctx) = {
			implicit val ec = gql.ec
			gql.execute[JsValue](query, variables = Json.obj(args: _*)).map(json => (json \ "data").as[JsObject])
		}
	}
}
