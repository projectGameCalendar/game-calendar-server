package com.projectgc.calendar.service

import com.projectgc.calendar.model.api.DateReleasesResponse
import com.projectgc.calendar.model.api.RangeReleasesResponse
import com.projectgc.calendar.model.api.ReleasesResponse
import com.projectgc.calendar.repository.api.ServiceApiRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.springframework.stereotype.Service

/**
 * Coordinates release lookup rules before exposing them to the web layer.
 */
@Service
class GameReleaseService(
    private val repository: ServiceApiRepository,
) {
    fun findReleases(
        date: String?,
        from: String?,
        to: String?,
        platformGroupParams: List<String>?,
    ): ReleasesResponse {
        val query = parseReleaseQuery(date, from, to)
        val platformIds = parsePlatformIds(platformGroupParams)
        val releases = repository.findReleases(
            startInclusive = query.from.atStartOfDay(SERVICE_ZONE).toInstant(),
            endExclusive = query.to.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant(),
            platformIds = platformIds,
        )

        return if (query.singleDate) {
            DateReleasesResponse(
                date = query.from.toString(),
                count = releases.size,
                releases = releases,
            )
        } else {
            RangeReleasesResponse(
                from = query.from.toString(),
                to = query.to.toString(),
                count = releases.size,
                releases = releases,
            )
        }
    }

    private fun parseReleaseQuery(
        date: String?,
        from: String?,
        to: String?,
    ): ReleaseQuery {
        val hasDate = !date.isNullOrBlank()
        val hasFrom = !from.isNullOrBlank()
        val hasTo = !to.isNullOrBlank()

        if (hasDate && (hasFrom || hasTo)) {
            throw badRequest("date cannot be used with from or to")
        }
        if (!hasDate && !hasFrom && !hasTo) {
            throw badRequest("date or from/to is required")
        }
        if (hasFrom != hasTo) {
            throw badRequest("from and to must be used together")
        }

        if (hasDate) {
            val parsedDate = parseDate(date, "date")
            return ReleaseQuery(parsedDate, parsedDate, singleDate = true)
        }

        val parsedFrom = parseDate(from, "from")
        val parsedTo = parseDate(to, "to")
        if (parsedFrom > parsedTo) {
            throw badRequest("from must be before or equal to to")
        }

        val inclusiveDays = ChronoUnit.DAYS.between(parsedFrom, parsedTo) + 1
        if (inclusiveDays > MAX_RANGE_DAYS) {
            throw badRequest("date range must be $MAX_RANGE_DAYS days or less")
        }

        return ReleaseQuery(parsedFrom, parsedTo, singleDate = false)
    }

    private fun parseDate(value: String?, parameterName: String): LocalDate {
        val normalized = value?.trim()
        if (normalized == null || !DATE_PATTERN.matches(normalized)) {
            throw badRequest("$parameterName must be YYYY-MM-DD")
        }

        return try {
            LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (ex: DateTimeParseException) {
            throw badRequest("$parameterName must be a valid date")
        }
    }

    private fun parsePlatformIds(values: List<String>?): Set<Long> {
        if (values.isNullOrEmpty()) {
            return emptySet()
        }

        return values
            .flatMap { it.split(",") }
            .map { it.trim() }
            .map {
                if (it.isEmpty()) {
                    throw badRequest("platformGroup must not be blank")
                }
                it.uppercase(Locale.US)
            }
            .flatMap {
                try {
                    PlatformGroup.valueOf(it).platformIds
                } catch (ex: IllegalArgumentException) {
                    throw badRequest("unknown platformGroup: $it")
                }
            }
            .toSet()
    }

    private fun badRequest(message: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private data class ReleaseQuery(
        val from: LocalDate,
        val to: LocalDate,
        val singleDate: Boolean,
    )

    companion object {
        private const val MAX_RANGE_DAYS = 62L
        private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
