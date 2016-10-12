import com.google.inject.{Inject, Singleton}
import filters.{EnsureJson, JsonPrettyfier, UnauthorizedHeader}
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter

@Singleton
class Filters @Inject()(
		jsonPrettyfier: JsonPrettyfier,
		unauthorizedHeader: UnauthorizedHeader,
		ensureJson: EnsureJson,
		corsFilter: CORSFilter) extends HttpFilters {

	override def filters: Seq[EssentialFilter] = Seq(
		jsonPrettyfier,
		unauthorizedHeader,
		ensureJson,
		corsFilter
	)
}
