package com.projectgc.batch.model.mapper

import com.projectgc.batch.model.dto.igdb.*
import com.projectgc.batch.model.entity.ingest.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("IngestEntityMappers")

private fun String?.toUuid(): UUID? = this?.let {
    try {
        UUID.fromString(it)
    } catch (e: IllegalArgumentException) {
        log.warn("UUID 파싱 실패, null 처리: '{}'", it)
        null
    }
}

fun IgdbGameDto.toEntity() = IngestGameEntity().also {
    it.id = id
    it.name = name.orEmpty()
    it.slug = slug.orEmpty()
    it.summary = summary
    it.storyline = storyline
    it.firstReleaseDate = firstReleaseDate
    it.releaseDateIds = releaseDates
    it.platformIds = platforms
    it.gameStatusId = gameStatus
    it.gameTypeId = gameType
    it.languageSupportIds = languageSupports
    it.genreIds = genres
    it.themeIds = themes
    it.playerPerspectiveIds = playerPerspectives
    it.gameModeIds = gameModes
    it.keywordIds = keywords
    it.involvedCompanyIds = involvedCompanies
    it.parentGameId = parentGame
    it.remakeIds = remakes
    it.remasterIds = remasters
    it.portIds = ports
    it.standaloneExpansionIds = standaloneExpansions
    it.similarGameIds = similarGames
    it.coverId = cover
    it.artworkIds = artworks
    it.screenshotIds = screenshots
    it.videoIds = videos
    it.websiteIds = websites
    it.alternativeNameIds = alternativeNames
    it.gameLocalizationIds = gameLocalizations
    it.tagNumbers = tags
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbReleaseDateDto.toEntity() = IngestReleaseDateEntity().also {
    it.id = id
    it.gameId = game
    it.platformId = platform
    it.releaseRegionId = releaseRegion
    it.statusId = status
    it.releaseTimestamp = date
    it.releaseYear = y
    it.releaseMonth = m
    it.humanReadableDate = human
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbPlatformDto.toEntity() = IngestPlatformEntity().also {
    it.id = id
    it.name = name.orEmpty()
    it.abbreviation = abbreviation
    it.alternativeName = alternativeName
    it.platformLogoId = platformLogo
    it.platformTypeId = platformType
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbCompanyDto.toEntity() = IngestCompanyEntity().also {
    it.id = id
    it.name = name.orEmpty()
    it.parentCompanyId = parent
    it.changedCompanyId = changedCompanyId
    it.developedGameIds = developed
    it.publishedGameIds = published
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbInvolvedCompanyDto.toEntity() = IngestInvolvedCompanyEntity().also {
    it.id = id
    it.gameId = game
    it.companyId = company
    it.developer = developer
    it.publisher = publisher
    it.porting = porting
    it.supporting = supporting
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbCoverDto.toEntity() = IngestCoverEntity().also {
    it.id = id
    it.gameId = game
    it.gameLocalizationId = gameLocalization
    it.imageId = imageId.orEmpty()
    it.url = url
    it.checksum = checksum.toUuid()
}

fun IgdbLanguageSupportDto.toEntity() = IngestLanguageSupportEntity().also {
    it.id = id
    it.gameId = game
    it.languageId = language
    it.languageSupportTypeId = languageSupportType
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbGameLocalizationDto.toEntity() = IngestGameLocalizationEntity().also {
    it.id = id
    it.gameId = game
    it.regionId = region
    it.name = name.orEmpty()
    it.coverId = cover
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

// Named entities — IgdbNamedDto 재사용 (엔드포인트별 전용 toXxxEntity 함수)
fun IgdbNamedDto.toGenreEntity() = IngestGenreEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toThemeEntity() = IngestThemeEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toPlayerPerspectiveEntity() = IngestPlayerPerspectiveEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toGameModeEntity() = IngestGameModeEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toKeywordEntity() = IngestKeywordEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toLanguageSupportTypeEntity() = IngestLanguageSupportTypeEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbNamedDto.toPlatformTypeEntity() = IngestPlatformTypeEntity().also {
    it.id = id; it.name = name.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbGameStatusDto.toEntity() = IngestGameStatusEntity().also {
    it.id = id; it.status = status.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbGameTypeDto.toEntity() = IngestGameTypeEntity().also {
    it.id = id; it.type = type.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbWebsiteTypeDto.toEntity() = IngestWebsiteTypeEntity().also {
    it.id = id; it.type = type.orEmpty(); it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbLanguageDto.toEntity() = IngestLanguageEntity().also {
    it.id = id
    it.locale = locale.orEmpty()
    it.englishName = name.orEmpty()
    it.nativeName = nativeName
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbRegionDto.toEntity() = IngestRegionEntity().also {
    it.id = id
    it.name = name.orEmpty()
    it.identifier = identifier
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbReleaseDateRegionDto.toEntity() = IngestReleaseDateRegionEntity().also {
    it.id = id; it.regionName = region; it.checksum = checksum.toUuid(); it.updatedAt = updatedAt
}

fun IgdbReleaseDateStatusDto.toEntity() = IngestReleaseDateStatusEntity().also {
    it.id = id
    it.name = name.orEmpty()
    it.description = description
    it.checksum = checksum.toUuid()
    it.updatedAt = updatedAt
}

fun IgdbPlatformLogoDto.toEntity() = IngestPlatformLogoEntity().also {
    it.id = id; it.imageId = imageId.orEmpty(); it.url = url; it.checksum = checksum.toUuid()
}

// Media entities — IgdbImageDto 재사용 (artwork / screenshot 구분)
fun IgdbImageDto.toArtworkEntity() = IngestArtworkEntity().also {
    it.id = id; it.gameId = game; it.imageId = imageId.orEmpty(); it.url = url; it.checksum = checksum.toUuid()
}

fun IgdbImageDto.toScreenshotEntity() = IngestScreenshotEntity().also {
    it.id = id; it.gameId = game; it.imageId = imageId.orEmpty(); it.url = url; it.checksum = checksum.toUuid()
}

fun IgdbGameVideoDto.toEntity() = IngestGameVideoEntity().also {
    it.id = id; it.gameId = game; it.name = name; it.videoId = videoId.orEmpty(); it.checksum = checksum.toUuid()
}

fun IgdbWebsiteDto.toEntity() = IngestWebsiteEntity().also {
    it.id = id; it.gameId = game; it.typeId = type; it.url = url.orEmpty(); it.trusted = trusted; it.checksum = checksum.toUuid()
}

fun IgdbAlternativeNameDto.toEntity() = IngestAlternativeNameEntity().also {
    it.id = id; it.gameId = game; it.name = name.orEmpty(); it.comment = comment; it.checksum = checksum.toUuid()
}
