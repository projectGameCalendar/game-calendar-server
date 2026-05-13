package com.projectgc.calendar.model.api

import com.fasterxml.jackson.annotation.JsonProperty

sealed interface ReleasesResponse

data class RangeReleasesResponse(
    val from: String,
    val to: String,
    val count: Int,
    val releases: List<ReleaseItemResponse>,
) : ReleasesResponse

data class DateReleasesResponse(
    val date: String,
    val count: Int,
    val releases: List<ReleaseItemResponse>,
) : ReleasesResponse

data class ReleaseItemResponse(
    val releaseIds: List<Long>,
    val gameId: Long,
    val date: String,
    val title: String,
    val defaultTitle: String,
    val gameType: GameTypeResponse?,
    val region: ReleaseRegionResponse?,
    val releaseStatus: ReleaseStatusResponse?,
    val platforms: List<PlatformResponse>,
    val coverThumbnailUrl: String?,
    val koreanLanguageSupport: KoreanLanguageSupportResponse?,
)

data class GameDetailResponse(
    val gameId: Long,
    val slug: String?,
    val title: String,
    val defaultTitle: String,
    val summary: String?,
    val firstReleaseDate: String?,
    val gameType: GameTypeResponse?,
    val gameStatus: GameStatusResponse?,
    val coverThumbnailUrl: String?,
    val coverUrl: String?,
    val platforms: List<PlatformResponse>,
    val genres: List<NamedResourceResponse>,
    val developers: List<NamedResourceResponse>,
    val koreanLanguageSupport: KoreanLanguageSupportResponse?,
    val websites: List<WebsiteResponse>,
    val video: VideoResponse?,
)

data class GameTypeResponse(
    val id: Long,
    val type: String?,
)

data class GameStatusResponse(
    val id: Long,
    val status: String?,
)

data class ReleaseRegionResponse(
    val id: Long,
    val name: String?,
)

data class ReleaseStatusResponse(
    val id: Long,
    val name: String?,
)

data class PlatformResponse(
    val id: Long,
    val name: String?,
    val abbreviation: String?,
)

data class NamedResourceResponse(
    val id: Long,
    val name: String?,
)

data class KoreanLanguageSupportResponse(
    val audio: Boolean,
    val subtitles: Boolean,
    @get:JsonProperty("interface")
    val interfaceSupported: Boolean,
)

data class WebsiteResponse(
    val id: Long,
    val url: String,
    val type: WebsiteTypeResponse?,
)

data class WebsiteTypeResponse(
    val id: Long,
    val type: String?,
)

data class VideoResponse(
    val id: Long,
    val name: String?,
    val videoId: String,
    val url: String,
    val thumbnailUrl: String,
)
