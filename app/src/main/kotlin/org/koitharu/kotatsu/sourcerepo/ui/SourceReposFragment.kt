package org.koitharu.kotatsu.sourcerepo.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.sourcerepo.domain.PluginEntry
import org.koitharu.kotatsu.sourcerepo.domain.PluginState

@AndroidEntryPoint
class SourceReposFragment : BasePreferenceFragment(R.string.source_repos) {

	private val viewModel by viewModels<SourceReposViewModel>()

	private val downloadReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
			if (id != -1L) {
				viewModel.onDownloadComplete(id)
			}
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_source_repos)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.state.observe(viewLifecycleOwner, ::render)
		viewModel.onInstall.observeEvent(viewLifecycleOwner) { startActivity(it) }
		viewModel.onMessage.observeEvent(viewLifecycleOwner) {
			Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
		}
	}

	override fun onStart() {
		super.onStart()
		ContextCompat.registerReceiver(
			requireContext(),
			downloadReceiver,
			IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
			ContextCompat.RECEIVER_EXPORTED,
		)
	}

	override fun onStop() {
		requireContext().unregisterReceiver(downloadReceiver)
		super.onStop()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_ADD -> {
			showAddDialog()
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	private fun render(state: SourceReposViewModel.State) {
		val reposCategory = findPreference<PreferenceCategory>(KEY_REPOS) ?: return
		val pluginsCategory = findPreference<PreferenceCategory>(KEY_PLUGINS) ?: return
		reposCategory.removeAll()
		pluginsCategory.removeAll()
		val context = requireContext()
		if (state.repos.isEmpty()) {
			reposCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					isSelectable = false
					setSummary(R.string.no_repositories)
				},
			)
		}
		for (url in state.repos) {
			reposCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					title = url
					summary = state.errors[url]?.getDisplayMessage(resources)
					setOnPreferenceClickListener {
						confirmRemove(url)
						true
					}
				},
			)
		}
		for (entry in state.plugins) {
			pluginsCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					title = entry.plugin.name
					summary = describe(entry)
					setOnPreferenceClickListener {
						onPluginClick(entry)
						true
					}
				},
			)
		}
	}

	private fun describe(entry: PluginEntry): String {
		val type = getString(
			when (entry.plugin.mediaType) {
				MediaType.MANGA -> R.string.media_type_manga
				MediaType.BOOK -> R.string.media_type_book
				MediaType.VIDEO -> R.string.media_type_video
			},
		)
		val status = when (entry.state) {
			PluginState.NOT_INSTALLED -> null
			PluginState.INSTALLED -> getString(R.string.plugin_installed)
			PluginState.UPDATE_AVAILABLE -> getString(R.string.plugin_update_available)
		}
		return listOfNotNull("v${entry.plugin.versionName}", type, status).joinToString(" · ")
	}

	private fun onPluginClick(entry: PluginEntry) {
		if (entry.state == PluginState.INSTALLED) {
			Toast.makeText(requireContext(), R.string.plugin_up_to_date, Toast.LENGTH_SHORT).show()
		} else {
			viewModel.install(entry.plugin)
		}
	}

	private fun confirmRemove(url: String) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.remove_repository_confirm)
			.setMessage(url)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.remove) { _, _ -> viewModel.removeRepo(url) }
			.show()
	}

	private fun showAddDialog() {
		val context = requireContext()
		val input = EditText(context).apply {
			setHint(R.string.repository_url_hint)
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
			setSingleLine()
		}
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val container = FrameLayout(context).apply {
			setPadding(padding, padding / 2, padding, 0)
			addView(input)
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.add_repository)
			.setView(container)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add) { _, _ ->
				if (!viewModel.addRepo(input.text.toString())) {
					Toast.makeText(context, R.string.repository_invalid_url, Toast.LENGTH_SHORT).show()
				}
			}
			.show()
	}

	private companion object {

		const val KEY_ADD = "source_repo_add"
		const val KEY_REPOS = "source_repos_list"
		const val KEY_PLUGINS = "source_plugins_list"
	}
}
