package org.koitharu.kotatsu.explore.ui.preset

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.explore.data.SourcePreset
import org.koitharu.kotatsu.explore.data.SourcePresetsRepository
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import javax.inject.Inject

@HiltViewModel
class SourcePresetEditViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val presetsRepository: SourcePresetsRepository,
	private val sourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {

	private val presetId = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	val onSaved = MutableEventFlow<Unit>()
	val preset = MutableStateFlow<SourcePreset?>(null)

	val allLocales: Set<String> = sourcesRepository.getPresetLanguages()

	init {
		launchLoadingJob(Dispatchers.Default) {
			preset.value = if (presetId != NO_ID) {
				presetsRepository.getById(presetId)
			} else {
				null
			}
		}
	}

	fun save(title: String, selectedLanguages: Set<String>) {
		launchLoadingJob(Dispatchers.Default) {
			check(title.isNotEmpty())
			if (presetId == NO_ID) {
				val initialSources = getSourcesForLanguages(selectedLanguages)
				presetsRepository.createPreset(title, selectedLanguages, initialSources)
			} else {
				val current = preset.value
				presetsRepository.updatePreset(presetId, title, selectedLanguages)
				if (current != null && current.languages != selectedLanguages) {
					val oldEligible = getSourcesForLanguages(current.languages)
					val explicitlyRemoved = oldEligible - current.sources
					val nonLanguageExtras = current.sources - oldEligible
					val newSources = (getSourcesForLanguages(selectedLanguages) - explicitlyRemoved) + nonLanguageExtras
					presetsRepository.updatePresetSources(presetId, newSources)
				}
			}
			onSaved.call(Unit)
		}
	}

	private fun getSourcesForLanguages(languages: Set<String>): Set<String> =
		sourcesRepository.getSourceNamesForPresetLanguages(languages)

	companion object {
		const val NO_ID = -1L
	}
}
