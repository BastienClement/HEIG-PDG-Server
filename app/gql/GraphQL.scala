package gql

import com.google.inject.Inject
import sangria.ast.Document
import sangria.execution.{ExecutionScheme, Executor, QueryReducer}
import sangria.marshalling.{InputUnmarshaller, ResultMarshaller}
import scala.concurrent.ExecutionContext
import services.LocationService

class GraphQL @Inject()()
		(implicit val ec: ExecutionContext, val ls: LocationService)
		extends Root {

	private val rejectComplexQueries = QueryReducer.rejectComplexQueries[Any](1000, (c, ctx) =>
		new IllegalArgumentException(s"Too complex query"))

	private val executor = Executor(
		schema = RootSchema,
		deferredResolver = resolver,
		maxQueryDepth = Some(7),
		queryReducers = List(rejectComplexQueries))

	def execute[Input](doc: Document, variables: Input = InputUnmarshaller.emptyMapVars, operation: Option[String] = None)
			(implicit
					ctx: Ctx,
					marshaller: ResultMarshaller,
					um: InputUnmarshaller[Input],
					scheme: ExecutionScheme): scheme.Result[Ctx, marshaller.Node] = {
		executor.execute(doc, ctx, (), operation, variables)
	}
}
