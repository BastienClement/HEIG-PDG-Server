package utils

import play.api.libs.json.{JsObject, Reads}
import scala.concurrent.{ExecutionContext, Future}
import slick.dbio.Effect.Write
import slick.lifted.{FlatShapeLevel, Shape}
import utils.Patch.Mapping
import utils.SlickAPI.{Query, _}

/**
  * Description of a Patch operation.
  *
  * @param query    the row selection query
  * @param mappings the list of patch mappings to apply
  */
case class Patch[E, U, C[_]](query: Query[E, U, C], mappings: Vector[Mapping[E, U, C]] = Vector.empty) {
	private type FShape[F, T, G] = Shape[_ <: FlatShapeLevel, F, T, G]
	private type Self = Patch[E, U, C]

	/** Add a new mapping to this Patch operation */
	private def withMapping(mapping: Mapping[E, U, C]): Self = copy(mappings = mappings :+ mapping)

	/** Defines a generic mapping */
	def Map[F, G, T](extractor: JsObject => Option[T], fields: E => F)(implicit shape: FShape[F, T, G]): Self = {
		withMapping(new Mapping[E, U, C] {
			def materialize(query: Query[E, U, C], document: JsObject): Option[Action] = {
				extractor(document).map(value => query.map(fields).update(value))
			}
		})
	}

	/** Defines a simple field mapping */
	def MapField[T: Reads, G](key: String, column: E => Rep[T])(implicit shape: FShape[Rep[T], T, G]): Self = {
		Map(doc => (doc \ key).asOpt[T], column)
	}

	/** Execute the operation, using the provided patch document */
	def Execute(document: JsObject)(implicit ec: ExecutionContext): Future[U] = {
		query.exists.result.flatMap {
			case true =>
				val actions = mappings.map(m => m.materialize(query, document)).collect { case Some(action) => action }
				DBIO.sequence(actions) andThen query.result.head
			case false =>
				DBIO.failed(new NoSuchElementException)
		}.transactionally.run
	}
}

object Patch {
	sealed trait Mapping[E, U, C[_]] {
		type Action = DBIOAction[Int, NoStream, Write]
		def materialize(query: Query[E, U, C], document: JsObject): Option[Action]
	}

	def apply[E, U, C[_]](query: Query[E, U, C]): Patch[E, U, C] = new Patch(query)
}
