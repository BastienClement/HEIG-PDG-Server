import com.google.inject.{Inject, Singleton}
import filters.UnauthorizedHeader
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter

@Singleton
class Filters @Inject()(
		unauthorizedHeader: UnauthorizedHeader,
		corsFilter: CORSFilter) extends HttpFilters {

	override def filters: Seq[EssentialFilter] = Seq(
		unauthorizedHeader,
		corsFilter
	)
}
