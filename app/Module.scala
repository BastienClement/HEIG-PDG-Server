import com.google.inject.AbstractModule
import java.time.Clock
import services.Uptime
import utils.SlickAPI

class Module extends AbstractModule {
	override def configure() = {
		bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
		bind(classOf[Uptime]).asEagerSingleton()
		requestInjection(SlickAPI)
	}
}
