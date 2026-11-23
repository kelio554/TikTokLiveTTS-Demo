package net.mkhalil.taktik.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.data.remote.TikTokUserApi
import net.mkhalil.taktik.data.repo.SettingsRepository

class ViewerDetailViewModel(
    handle: SavedStateHandle,
    private val viewerDao: ViewerDao,
    private val eventDao: EventDao,
    private val userApi: TikTokUserApi,
    settings: SettingsRepository,
) : ViewModel() {

    data class Totals(
        val comments: Int = 0,
        val likes: Long = 0,
        val gifts: Int = 0,
        val giftCoins: Long = 0,
        val follows: Int = 0,
        val shares: Int = 0,
    )

    data class UiState(
        val viewer: ViewerEntity? = null,
        val totals: Totals = Totals(),
        val events: List<EventEntity> = emptyList(),
        val coinToUsd: Double = SettingsRepository.DEFAULT_COIN_USD,
        val refreshing: Boolean = false,
    )

    private val id: String = handle.get<String>("userId").orEmpty()

    private val viewerFlow = if (id.isEmpty()) flowOf<ViewerEntity?>(null) else viewerDao.observeById(id)
    private val eventsFlow = if (id.isEmpty()) flowOf(emptyList()) else eventDao.observeByViewer(id, 500)

    val state = combine(viewerFlow, eventsFlow, settings.settings) { v, evts, sets ->
        UiState(
            viewer = v,
            totals = totalize(evts),
            events = evts,
            coinToUsd = sets.coinToUsd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun refreshProfile() {
        val v = state.value.viewer ?: return
        viewModelScope.launch {
            val res = runCatching { userApi.fetchProfile(v.unique_id) }.getOrNull() ?: return@launch
            viewerDao.upsert(
                v.copy(
                    nickname = res.nickname ?: v.nickname,
                    avatar_url = res.avatarUrl ?: v.avatar_url,
                    region = res.region ?: v.region,
                    region_code = res.regionCode ?: v.region_code,
                    signature = res.signature ?: v.signature,
                    follower_count = res.followerCount ?: v.follower_count,
                    following_count = res.followingCount ?: v.following_count,
                    verified = res.verified || v.verified,
                    region_fetched_at = System.currentTimeMillis(),
                    last_seen_at = v.last_seen_at,
                )
            )
        }
    }

    private fun totalize(events: List<EventEntity>): Totals {
        var comments = 0
        var likes = 0L
        var gifts = 0
        var giftCoins = 0L
        var follows = 0
        var shares = 0
        for (e in events) when (e.type) {
            "COMMENT" -> comments++
            "LIKE" -> likes += (e.like_count ?: 0).toLong()
            "GIFT" -> { gifts++; giftCoins += (e.gift_coins ?: 0) * (e.gift_count ?: 1) }
            "FOLLOW" -> follows++
            "SHARE" -> shares++
        }
        return Totals(comments, likes, gifts, giftCoins, follows, shares)
    }
}
