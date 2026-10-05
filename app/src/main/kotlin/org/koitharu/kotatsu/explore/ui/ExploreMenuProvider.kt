package org.koitharu.kotatsu.explore.ui

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.explore.ui.preset.SourcePresetListActivity

class ExploreMenuProvider(
	private val context: Context,
	private val router: AppRouter,
	private val viewModel: ExploreViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_explore, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(
			when (viewModel.mediaTypeFilter) {
				null -> R.id.action_media_all
				MediaType.MANGA -> R.id.action_media_manga
				MediaType.BOOK -> R.id.action_media_book
				MediaType.VIDEO -> R.id.action_media_video
			},
		)?.isChecked = true
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_manage -> {
				router.openSourcesSettings()
				true
			}

			R.id.action_presets -> {
				context.startActivity(android.content.Intent(context, SourcePresetListActivity::class.java))
				true
			}

			R.id.action_media_all -> setMediaType(menuItem, null)
			R.id.action_media_manga -> setMediaType(menuItem, MediaType.MANGA)
			R.id.action_media_book -> setMediaType(menuItem, MediaType.BOOK)
			R.id.action_media_video -> setMediaType(menuItem, MediaType.VIDEO)

			else -> false
		}
	}

	private fun setMediaType(menuItem: MenuItem, type: MediaType?): Boolean {
		menuItem.isChecked = true
		viewModel.setMediaTypeFilter(type)
		return true
	}
}
