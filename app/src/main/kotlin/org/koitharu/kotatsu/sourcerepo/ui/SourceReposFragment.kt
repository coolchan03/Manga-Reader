package org.koitharu.kotatsu.sourcerepo.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.source.JsExtensionProvider
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.sourcerepo.domain.JsSourceItem
import org.koitharu.kotatsu.sourcerepo.domain.PluginEntry
import org.koitharu.kotatsu.sourcerepo.domain.PluginState
import org.koitharu.kotatsu.sourcerepo.domain.RepoPreview
import javax.inject.Inject

@AndroidEntryPoint
class SourceReposFragment : BasePreferenceFragment(R.string.source_repos) {

	private val viewModel by viewModels<SourceReposViewModel>()

	@Inject
	lateinit var jsExtensions: JsExtensionProvider

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
		viewModel.onRepoPreview.observeEvent(viewLifecycleOwner, ::showRepoPreview)
		viewModel.onRepoPreviewError.observeEvent(viewLifecycleOwner) {
			Toast.makeText(
				requireContext(),
				getString(R.string.repository_verify_failed, it.getDisplayMessage(resources)),
				Toast.LENGTH_LONG,
			).show()
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
		if (state.skippedDartCount > 0) {
			pluginsCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					isSelectable = false
					summary = getString(R.string.dart_sources_skipped, state.skippedDartCount)
				},
			)
		}
		for (item in state.jsSources) {
			pluginsCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					title = item.entry.name
					summary = describeJs(item)
					setOnPreferenceClickListener {
						onJsSourceClick(item)
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

	private fun describeJs(item: JsSourceItem): String {
		val e = item.entry
		val type = getString(
			when (e.itemType) {
				JsItemType.MANGA -> R.string.media_type_manga
				JsItemType.NOVEL -> R.string.media_type_book
				JsItemType.ANIME -> R.string.media_type_video
			},
		)
		val status = when (item.state) {
			PluginState.NOT_INSTALLED -> null
			PluginState.INSTALLED -> getString(R.string.plugin_installed)
			PluginState.UPDATE_AVAILABLE -> getString(R.string.plugin_update_available)
		}
		return listOfNotNull("v${e.version}", e.lang.takeIf { it.isNotEmpty() }, type, status, "Site availability not verified").joinToString(" · ")
	}

	private fun onJsSourceClick(item: JsSourceItem) {
		when (item.state) {
			PluginState.NOT_INSTALLED -> viewModel.installJs(item.entry)
			PluginState.INSTALLED -> showJsSourceActions(item, canUpdate = false)
			PluginState.UPDATE_AVAILABLE -> showJsSourceActions(item, canUpdate = true)
		}
	}

	private fun showJsSourceActions(item: JsSourceItem, canUpdate: Boolean) {
		val labels = ArrayList<CharSequence>(3)
		val actions = ArrayList<() -> Unit>(3)
		if (canUpdate) {
			labels += getString(R.string.update)
			actions += { viewModel.installJs(item.entry) }
		}
		labels += getString(R.string.settings)
		actions += { openJsSourceSettings(item.entry) }
		if (item.entry.baseUrl.startsWith("http")) {
			labels += getString(R.string.open_in_browser)
			actions += {
				startActivity(
					AppRouter.browserIntent(
						requireContext(),
						item.entry.baseUrl,
						JsMangaSource(item.entry.id),
						item.entry.name,
					),
				)
			}
		}
		labels += getString(R.string.remove)
		actions += { confirmJsSourceRemove(item.entry) }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(item.entry.name)
			.setItems(labels.toTypedArray()) { _, which -> actions[which]() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun confirmJsSourceRemove(entry: JsSourceEntry) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(entry.name)
			.setMessage(R.string.js_source_remove_confirm)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.remove) { _, _ -> viewModel.uninstallJs(entry) }
			.show()
	}

	private fun openJsSourceSettings(entry: JsSourceEntry) {
		viewLifecycleOwner.lifecycleScope.launch {
			val preferences = jsExtensions.getSourcePreferences(entry.id)
			if (preferences.isEmpty()) {
				Toast.makeText(requireContext(), R.string.js_source_no_preferences, Toast.LENGTH_SHORT).show()
				return@launch
			}
			showJsSourcePreferences(entry, preferences)
		}
	}

	private fun showJsSourcePreferences(entry: JsSourceEntry, preferences: List<JsonObject>) {
		val context = requireContext()
		val density = resources.displayMetrics.density
		val gap = (8 * density).toInt()
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val fields = ArrayList<Pair<String, () -> String?>>()
		val content = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, gap, padding, gap)
		}

		fun addHeading(title: String, summary: String?) {
			content.addView(TextView(context).apply {
				text = title
				setPadding(0, gap, 0, 0)
			})
			if (!summary.isNullOrBlank()) {
				content.addView(TextView(context).apply {
					text = summary
					alpha = 0.72f
				})
			}
		}

		for (definition in preferences) {
			val key = definition.string("key") ?: continue
			val stored = jsExtensions.getPreference(entry.id, key)

			val editText = definition["editTextPreference"] as? JsonObject
			if (editText != null) {
				addHeading(editText.string("title") ?: key, editText.string("summary"))
				val defaultValue = editText.string("value").orEmpty()
				val input = EditText(context).apply {
					setSingleLine(false)
					setText(stored ?: defaultValue)
					hint = editText.string("dialogMessage")
					inputType = if ((stored ?: defaultValue).startsWith("http")) {
						InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
					} else {
						InputType.TYPE_CLASS_TEXT
					}
				}
				content.addView(input)
				fields += key to { input.text?.toString() }
				continue
			}

			val toggle = (definition["switchPreferenceCompat"] as? JsonObject)
				?: (definition["checkBoxPreference"] as? JsonObject)
			if (toggle != null) {
				val box = CheckBox(context).apply {
					text = toggle.string("title") ?: key
					isChecked = stored?.toBooleanStrictOrNull()
						?: toggle.boolean("value")
						?: false
				}
				content.addView(box)
				toggle.string("summary")?.takeIf { it.isNotBlank() }?.let { summary ->
					content.addView(TextView(context).apply {
						text = summary
						alpha = 0.72f
					})
				}
				fields += key to { box.isChecked.toString() }
				continue
			}

			val list = definition["listPreference"] as? JsonObject
			if (list != null) {
				val values = list.stringArray("entryValues")
				if (values.isEmpty()) continue
				val entries = list.stringArray("entries").takeIf { it.size == values.size } ?: values
				addHeading(list.string("title") ?: key, list.string("summary"))
				val spinner = Spinner(context).apply {
					adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, entries)
					val defaultIndex = list.int("valueIndex")?.coerceIn(0, values.lastIndex) ?: 0
					setSelection(values.indexOf(stored).takeIf { it >= 0 } ?: defaultIndex)
				}
				content.addView(spinner)
				fields += key to { values.getOrNull(spinner.selectedItemPosition) }
				continue
			}

			val multiSelect = definition["multiSelectListPreference"] as? JsonObject
			if (multiSelect != null) {
				val values = multiSelect.stringArray("entryValues")
				if (values.isEmpty()) continue
				val entries = multiSelect.stringArray("entries").takeIf { it.size == values.size } ?: values
				val defaults = multiSelect.stringArray("values").toSet()
				val selected = stored?.let { raw ->
					runCatching {
						(Json.parseToJsonElement(raw) as? JsonArray)
							.orEmpty()
							.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
							.toSet()
					}.getOrNull()
				} ?: defaults
				addHeading(multiSelect.string("title") ?: key, multiSelect.string("summary"))
				val boxes = entries.mapIndexed { index, label ->
					CheckBox(context).apply {
						text = label
						isChecked = values[index] in selected
						content.addView(this)
					}
				}
				fields += key to {
					JsonArray(
						boxes.mapIndexedNotNull { index, box ->
							values.getOrNull(index)?.takeIf { box.isChecked }?.let(::JsonPrimitive)
						},
					).toString()
				}
			}
		}

		if (fields.isEmpty()) {
			Toast.makeText(context, R.string.js_source_no_preferences, Toast.LENGTH_SHORT).show()
			return
		}
		val scroll = ScrollView(context).apply { addView(content) }
		MaterialAlertDialogBuilder(context)
			.setTitle(entry.name)
			.setView(scroll)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.apply) { _, _ ->
				viewLifecycleOwner.lifecycleScope.launch {
					for ((key, value) in fields) {
						jsExtensions.setPreference(entry.id, key, value())
					}
					Toast.makeText(context, R.string.js_source_preferences_saved, Toast.LENGTH_SHORT).show()
				}
			}
			.show()
	}

	private fun JsonObject.string(key: String): String? =
		(this[key] as? JsonPrimitive)?.contentOrNull

	private fun JsonObject.boolean(key: String): Boolean? =
		(this[key] as? JsonPrimitive)?.booleanOrNull

	private fun JsonObject.int(key: String): Int? =
		(this[key] as? JsonPrimitive)?.intOrNull

	private fun JsonObject.stringArray(key: String): List<String> =
		(this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

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
				if (!viewModel.previewRepo(input.text.toString())) {
					Toast.makeText(context, R.string.repository_invalid_url, Toast.LENGTH_SHORT).show()
				}
			}
			.show()
	}

	private fun showRepoPreview(preview: RepoPreview) {
		val context = requireContext()
		val names = (preview.jsSourceNames + preview.pluginNames).distinct()
		val shown = names.take(12)
		val remaining = names.size - shown.size
		val message = buildString {
			append(getString(R.string.repository_preview_url, preview.url))
			append("\n\n")
			append(getString(R.string.repository_preview_counts, preview.jsSourceNames.size, preview.pluginNames.size))
			if (preview.skippedDartCount > 0) {
				append("\n")
				append(getString(R.string.repository_preview_skipped, preview.skippedDartCount))
			}
			if (shown.isNotEmpty()) {
				append("\n\n")
				append(getString(R.string.repository_preview_contains))
				for (name in shown) append("\n- ").append(name)
				if (remaining > 0) append("\n").append(getString(R.string.repository_preview_more, remaining))
			}
			append("\n\n")
			append(getString(R.string.repository_trust_warning))
		}
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val body = TextView(context).apply {
			text = message
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		val scroll = ScrollView(context).apply { addView(body) }
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.repository_preview_title)
			.setView(scroll)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add) { _, _ -> viewModel.addRepo(preview.url) }
			.show()
	}

	private companion object {

		const val KEY_ADD = "source_repo_add"
		const val KEY_REPOS = "source_repos_list"
		const val KEY_PLUGINS = "source_plugins_list"
	}
}
