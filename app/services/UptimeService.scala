package services

import com.google.inject.{Inject, Singleton}
import java.time.{Clock, Instant}

@Singleton
class UptimeService @Inject() (clock: Clock) {
	val start: Instant = clock.instant
	def now: Long = clock.instant.getEpochSecond - start.getEpochSecond
}
