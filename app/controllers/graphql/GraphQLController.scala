package controllers.graphql

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Controller, Result}
import sangria.ast.Document
import sangria.execution.{ExecutionError, Executor, QueryReducer, QueryReducingError}
import sangria.marshalling.playJson._
import sangria.parser.QueryParser
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class GraphQLController @Inject() (val app: Provider[Application])
		extends Controller with Root with ApiActionBuilder {
	private val BAD_QUERY_JSON = UnprocessableEntity('GRAPHQL_BAD_QUERY withDetails "Failed to read query from JSON")
	private val BAD_QUERY_GRQL = UnprocessableEntity('GRAPHQL_BAD_QUERY withDetails "Empty request body")
	private val BAD_QUERY_UNSP = UnsupportedMediaType('GRAPHQL_BAD_QUERY withDetails "Unsupported Content-Type given")

	case class GraphQLQuery(query: Try[Document], variables: Option[JsValue], operation: Option[String])

	private def readGET(req: Ctx, query: String): Either[Result, GraphQLQuery] = {
		Right(GraphQLQuery(
			QueryParser.parse(query),
			req.getQueryString("variables").flatMap(v => Try(Json.parse(v)).toOption),
			req.getQueryString("operationName")
		))
	}

	private def readJsonPOST(req: Ctx): Either[Result, GraphQLQuery] = {
		val body = req.body.asJson
		body.flatMap(q => (q \ "query").asOpt[String]).map { query =>
			Right(GraphQLQuery(
				QueryParser.parse(query),
				body.flatMap(q => (q \ "variables").toOption),
				body.flatMap(q => (q \ "operationName").asOpt[String])
			))
		}.getOrElse(Left(BAD_QUERY_JSON))
	}

	private def readGraphqlPOST(req: Ctx): Either[Result, GraphQLQuery] = {
		req.body.asText.map { query =>
			Right(GraphQLQuery(
				QueryParser.parse(query),
				req.headers.get("X-GraphQL-Variables").flatMap(v => Try(Json.parse(v)).toOption),
				req.headers.get("X-GraphQL-Operation-Name").orElse(req.headers.get("X-GraphQL-Operation"))
			))
		}.getOrElse(Left(BAD_QUERY_GRQL))
	}

	private def readPOST(req: Ctx): Either[Result, GraphQLQuery] = req.contentType match {
		case Some("application/json") => readJsonPOST(req)
		case Some("application/graphql" | "text/plain") | None => readGraphqlPOST(req)
		case c => Left(BAD_QUERY_UNSP)
	}

	private def readQuery(req: Ctx): Either[Result, GraphQLQuery] = req.getQueryString("query") match {
		case Some(query) => readGET(req, query)
		case None => readPOST(req)
	}

	private val rejectComplexQueries = QueryReducer.rejectComplexQueries[Any](1000, (c, ctx) =>
		new IllegalArgumentException(s"Too complex query"))

	def graphql = ApiAction.async { req =>
		readQuery(req) match {
			case Right(GraphQLQuery(Success(query), variables, operation)) =>
				Executor.execute(
					RootSchema,
					query,
					req,
					variables = variables.getOrElse(Json.obj()),
					operationName = operation,
					queryReducers = List(rejectComplexQueries),
					maxQueryDepth = Some(7),
					deferredResolver = resolver
				).map(obj => Ok(obj)).recover {
					case e: ExecutionError => UnprocessableEntity('GRAPHQL_BAD_QUERY withDetails e.getMessage)
					case e: QueryReducingError => UnprocessableEntity('GRAPHQL_BAD_QUERY withDetails e.getMessage)
				}

			case Right(GraphQLQuery(Failure(e), _, _)) =>
				Future.successful(UnprocessableEntity('GRAPHQL_BAD_QUERY withCause e))

			case Left(res) =>
				Future.successful(res)
		}
	}
}
