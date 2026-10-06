package org.koitharu.kotatsu.search.ui.multi

import android.os.Build
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.search.domain.SearchKind

class SearchMenuProvider(
	private val activity: SearchActivity,
	private val viewModel: SearchViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_search_kind, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(
			when (viewModel.kind) {
				SearchKind.SIMPLE -> R.id.action_kind_simple
				SearchKind.TITLE -> R.id.action_kind_title
				SearchKind.AUTHOR -> R.id.action_kind_author
				SearchKind.TAG -> R.id.action_kind_tag
			},
		)?.isChecked = true
		menu.findItem(R.id.action_filter_hide_empty)?.isChecked = viewModel.isHideEmptyEnabled
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		when (menuItem.itemId) {
			R.id.action_filter_pinned_only -> {
				menuItem.isChecked = !menuItem.isChecked
				viewModel.setPinnedOnly(menuItem.isChecked)
				return true
			}

			R.id.action_filter_hide_empty -> {
				menuItem.isChecked = !menuItem.isChecked
				viewModel.setHideEmpty(menuItem.isChecked)
				return true
			}

			R.id.action_filter_years -> {
				showPublicationYearsDialog()
				return true
			}
		}

		val newKind = when (menuItem.itemId) {
			R.id.action_kind_simple -> SearchKind.SIMPLE
			R.id.action_kind_title -> SearchKind.TITLE
			R.id.action_kind_author -> SearchKind.AUTHOR
			R.id.action_kind_tag -> SearchKind.TAG
			else -> return false
		}
		if (newKind != viewModel.kind) {
			activity.router.openSearch(
				query = viewModel.query,
				kind = newKind,
			)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out, 0)
			} else {
				activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
			}
			activity.finishAfterTransition()
		}
		return true
	}

	private fun showPublicationYearsDialog() {
		val density = activity.resources.displayMetrics.density
		val padding = (24 * density).toInt()
		val container = LinearLayout(activity).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, 0, padding, 0)
		}
		val hint = TextView(activity).apply {
			setText(R.string.publication_year_filter_hint)
			setPadding(0, 0, 0, (8 * density).toInt())
		}
		val from = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			setHint(R.string.publication_year_from)
			setText(viewModel.publicationYearFrom?.toString().orEmpty())
		}
		val to = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			setHint(R.string.publication_year_to)
			setText(viewModel.publicationYearTo?.toString().orEmpty())
		}
		container.addView(hint)
		container.addView(from)
		container.addView(to)
		MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.publication_years)
			.setView(container)
			.setNeutralButton(R.string.any) { _, _ -> viewModel.setPublicationYearRange(null, null) }
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				viewModel.setPublicationYearRange(
					from.text?.toString()?.trim()?.toIntOrNull(),
					to.text?.toString()?.trim()?.toIntOrNull(),
				)
			}
			.show()
	}
}
