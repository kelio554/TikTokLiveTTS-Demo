package net.mkhalil.taktik.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import net.mkhalil.taktik.TaktikApp
import net.mkhalil.taktik.ui.connect.ConnectViewModel
import net.mkhalil.taktik.ui.history.HistoryViewModel
import net.mkhalil.taktik.ui.live.LiveViewModel
import net.mkhalil.taktik.ui.session.SessionDetailViewModel
import net.mkhalil.taktik.ui.settings.SettingsViewModel
import net.mkhalil.taktik.ui.viewer.ViewerDetailViewModel

object TaktikViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val s = TaktikApp.services
            ConnectViewModel(s.sessionManager, s.userApi)
        }
        initializer {
            val s = TaktikApp.services
            LiveViewModel(s.sessionManager, s.sessionDao, s.eventDao, s.viewerDao, s.settingsRepo)
        }
        initializer {
            val s = TaktikApp.services
            HistoryViewModel(s.sessionDao, s.viewerDao)
        }
        initializer {
            val s = TaktikApp.services
            SettingsViewModel(s.settingsRepo)
        }
        initializer {
            val s = TaktikApp.services
            SessionDetailViewModel(createSavedStateHandle(), s.sessionDao, s.viewerDao, s.eventDao, s.settingsRepo)
        }
        initializer {
            val s = TaktikApp.services
            ViewerDetailViewModel(createSavedStateHandle(), s.viewerDao, s.eventDao, s.userApi, s.settingsRepo)
        }
    }
}
