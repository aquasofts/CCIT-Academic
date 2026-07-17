package edu.ccit.webvpn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    repository: SettingsRepository,
    private val appIconController: AppIconController,
) : ViewModel() {
    val themeSettings = repository.themeSettings
    val uiSettings = repository.uiSettings
    val academicFeatureSettings = repository.academicFeatureSettings
    val rssFeedSettings = repository.rssFeedSettings
    val state = combine(themeSettings, uiSettings, ::AppearanceState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceState())

    fun setThemedAppIcon(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appIconController.setThemed(enabled) }
    }
}
