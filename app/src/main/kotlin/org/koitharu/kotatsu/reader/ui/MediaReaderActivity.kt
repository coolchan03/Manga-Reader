package org.koitharu.kotatsu.reader.ui

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.asJsSource
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.ui.dialog.setCheckbox
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityMediaReaderBinding
import org.koitharu.kotatsu.jsext.JsTrack
import org.koitharu.kotatsu.jsext.JsVideo
import org.koitharu.kotatsu.jsext.source.JsExtensionProvider
import org.koitharu.kotatsu.reader.ui.pager.ReaderUiState
import javax.inject.Inject

@AndroidEntryPoint
class MediaReaderActivity : BaseActivity<ActivityMediaReaderBinding>() {

	@Inject
	lateinit var extensions: JsExtensionProvider

	private val viewModel: ReaderViewModel by viewModels()
	private var loadJob: Job? = null
	private var player: ExoPlayer? = null
	private var currentChapterId: Long? = null
	private var currentMediaType: MediaType? = null
	private var restorePosition = 0
	private var textZoom = DEFAULT_TEXT_ZOOM

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMediaReaderBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

		configureWebView()
		textZoom = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
			.getInt(KEY_TEXT_ZOOM, DEFAULT_TEXT_ZOOM)
			.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM)
		viewBinding.webView.settings.textZoom = textZoom

		viewBinding.buttonPrevious.setOnClickListener {
			saveProgress()
			viewModel.switchChapterBy(-1)
		}
		viewBinding.buttonNext.setOnClickListener {
			saveProgress()
			viewModel.switchChapterBy(1)
		}
		viewBinding.buttonTextSmaller.setOnClickListener { adjustTextZoom(-TEXT_ZOOM_STEP) }
		viewBinding.buttonTextLarger.setOnClickListener { adjustTextZoom(TEXT_ZOOM_STEP) }

		viewModel.uiState.filterNotNull().observe(this, Lifecycle.State.STARTED, ::onUiState)
		viewModel.isLoading.observe(this) { viewBinding.progress.isVisible = it }
		viewModel.onLoadingError.observeEvent(this) { error ->
			showError(error.message ?: getString(R.string.media_reader_content_error), reload = true)
		}
		viewModel.onError.observeEvent(this) { error ->
			Snackbar.make(
				viewBinding.root,
				error.message ?: getString(R.string.media_reader_content_error),
				Snackbar.LENGTH_LONG,
			).show()
		}
		viewModel.onAskNsfwIncognito.observeEvent(this) { askForIncognitoMode() }
	}

	override fun getParentActivityIntent(): Intent? {
		val manga = viewModel.getMangaOrNull() ?: return null
		return AppRouter.detailsIntent(this, manga)
	}

	override fun onPause() {
		saveProgress()
		viewModel.onPause()
		super.onPause()
	}

	override fun onStop() {
		player?.pause()
		viewModel.onStop()
		super.onStop()
	}

	override fun onDestroy() {
		loadJob?.cancel()
		releasePlayer()
		viewBinding.webView.stopLoading()
		viewBinding.webView.destroy()
		super.onDestroy()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
		return insets
	}

	private fun configureWebView() {
		viewBinding.webView.apply {
			setBackgroundColor(Color.TRANSPARENT)
			settings.apply {
				javaScriptEnabled = false
				domStorageEnabled = false
				databaseEnabled = false
				allowFileAccess = false
				allowContentAccess = false
				mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
				builtInZoomControls = false
				displayZoomControls = false
			}
			webViewClient = object : WebViewClient() {
				override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

				@Suppress("DEPRECATION")
				override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

				override fun onPageFinished(view: WebView, url: String?) {
					val y = restorePosition
					restorePosition = 0
					if (y > 0) view.post { view.scrollTo(0, y) }
				}
			}
		}
	}

	private fun onUiState(state: ReaderUiState) {
		val manga = viewModel.getMangaOrNull() ?: return
		val mediaType = manga.source.mediaType()
		if (mediaType == MediaType.MANGA) {
			finish()
			return
		}
		currentMediaType = mediaType
		supportActionBar?.title = state.mangaName
		supportActionBar?.subtitle = state.getChapterTitle(resources)
		viewBinding.chapterStatus.text = getString(
			R.string.chapter_d_of_d,
			state.chapterNumber,
			state.chaptersTotal,
		)
		viewBinding.buttonPrevious.isEnabled = state.hasPreviousChapter()
		viewBinding.buttonNext.isEnabled = state.hasNextChapter()
		val isBook = mediaType == MediaType.BOOK
		viewBinding.buttonTextSmaller.isVisible = isBook
		viewBinding.buttonTextLarger.isVisible = isBook
		viewBinding.webView.isVisible = isBook
		viewBinding.playerView.isVisible = mediaType == MediaType.VIDEO

		if (currentChapterId != state.chapter.id) {
			saveProgress()
			currentChapterId = state.chapter.id
			restorePosition = viewModel.getCurrentState()?.scroll ?: 0
			when (mediaType) {
				MediaType.BOOK -> loadNovel(state)
				MediaType.VIDEO -> loadVideo(state)
				MediaType.MANGA -> Unit
			}
		}
	}

	private fun loadNovel(state: ReaderUiState) {
		releasePlayer()
		val manga = viewModel.getMangaOrNull() ?: return
		val jsSource = manga.source.asJsSource() ?: return showError(
			getString(R.string.media_reader_source_unsupported),
			reload = false,
		)
		viewBinding.progress.isVisible = true
		loadJob?.cancel()
		loadJob = lifecycleScope.launch {
			try {
				val html = extensions.get(jsSource.id).getHtmlContent(manga.title, state.chapter.url)
				viewBinding.webView.loadDataWithBaseURL(
					state.chapter.url,
					wrapHtml(html),
					"text/html",
					"UTF-8",
					null,
				)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				showError(e.message ?: getString(R.string.media_reader_content_error), reload = false)
			} finally {
				viewBinding.progress.isVisible = false
			}
		}
	}

	private fun loadVideo(state: ReaderUiState) {
		viewBinding.webView.stopLoading()
		val manga = viewModel.getMangaOrNull() ?: return
		val jsSource = manga.source.asJsSource() ?: return showError(
			getString(R.string.media_reader_source_unsupported),
			reload = false,
		)
		viewBinding.progress.isVisible = true
		loadJob?.cancel()
		loadJob = lifecycleScope.launch {
			try {
				val videos = extensions.get(jsSource.id).getVideoList(state.chapter.url)
				val selected = chooseVideo(videos)
					?: throw IllegalStateException(getString(R.string.media_reader_no_streams))
				startPlayer(selected)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				showError(e.message ?: getString(R.string.media_reader_content_error), reload = false)
			} finally {
				viewBinding.progress.isVisible = false
			}
		}
	}

	private fun chooseVideo(videos: List<JsVideo>): JsVideo? =
		videos.maxByOrNull { video ->
			QUALITY_NUMBER.find(video.quality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
		} ?: videos.firstOrNull()

	private fun startPlayer(video: JsVideo) {
		releasePlayer()
		val dataSourceFactory = DefaultHttpDataSource.Factory()
			.setAllowCrossProtocolRedirects(true)
			.setDefaultRequestProperties(video.headers.orEmpty())
		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
		val exoPlayer = ExoPlayer.Builder(this)
			.setMediaSourceFactory(mediaSourceFactory)
			.build()
		val subtitles = video.subtitles.mapNotNull(::subtitleConfiguration)
		val item = MediaItem.Builder()
			.setUri(video.url)
			.setSubtitleConfigurations(subtitles)
			.build()
		player = exoPlayer
		viewBinding.playerView.player = exoPlayer
		exoPlayer.setMediaItem(item, restorePosition.toLong().coerceAtLeast(0L))
		restorePosition = 0
		exoPlayer.prepare()
		exoPlayer.playWhenReady = true
	}

	private fun subtitleConfiguration(track: JsTrack): MediaItem.SubtitleConfiguration? {
		val file = track.file?.takeIf { it.isNotBlank() } ?: return null
		return MediaItem.SubtitleConfiguration.Builder(Uri.parse(file))
			.setLabel(track.label)
			.setMimeType(subtitleMimeType(file))
			.build()
	}

	private fun subtitleMimeType(url: String): String = when {
		url.substringBefore('?').endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
		url.substringBefore('?').endsWith(".ssa", ignoreCase = true) ||
			url.substringBefore('?').endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
		url.substringBefore('?').endsWith(".ttml", ignoreCase = true) ||
			url.substringBefore('?').endsWith(".xml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
		else -> MimeTypes.APPLICATION_SUBRIP
	}

	private fun releasePlayer() {
		viewBinding.playerView.player = null
		player?.release()
		player = null
	}

	private fun adjustTextZoom(delta: Int) {
		textZoom = (textZoom + delta).coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM)
		viewBinding.webView.settings.textZoom = textZoom
		getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
			.edit()
			.putInt(KEY_TEXT_ZOOM, textZoom)
			.apply()
	}

	private fun saveProgress() {
		when (currentMediaType) {
			MediaType.BOOK -> {
				val webView = viewBinding.webView
				val contentHeightPx = webView.contentHeight * webView.scale
				val scrollRange = (contentHeightPx - webView.height).coerceAtLeast(1f)
				val progress = (webView.scrollY / scrollRange).coerceIn(0f, 1f)
				viewModel.saveContinuousState(webView.scrollY, progress)
			}

			MediaType.VIDEO -> player?.let { current ->
				val duration = current.duration
				val position = current.currentPosition.coerceAtLeast(0L)
				val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
				viewModel.saveContinuousState(position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), progress)
			}

			else -> Unit
		}
	}

	private fun wrapHtml(content: String): String {
		val background = cssColor(
			MaterialColors.getColor(
				viewBinding.root,
				com.google.android.material.R.attr.colorSurface,
				Color.WHITE,
			),
		)
		val foreground = cssColor(
			MaterialColors.getColor(
				viewBinding.root,
				com.google.android.material.R.attr.colorOnSurface,
				Color.BLACK,
			),
		)
		return """
			<!doctype html>
			<html>
			<head>
			  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
			  <style>
			    :root { color-scheme: light dark; }
			    html, body { background: $background; color: $foreground; }
			    body {
			      margin: 0 auto;
			      max-width: 48rem;
			      padding: 1.25rem 1.35rem 4rem;
			      box-sizing: border-box;
			      font-family: sans-serif;
			      line-height: 1.68;
			      overflow-wrap: anywhere;
			    }
			    img, video, svg { max-width: 100%; height: auto; }
			    p { margin: 0 0 1em; }
			    a { color: inherit; }
			  </style>
			</head>
			<body>
			  $content
			</body>
			</html>
		""".trimIndent()
	}

	private fun cssColor(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

	private fun showError(message: String, reload: Boolean) {
		val bar = Snackbar.make(viewBinding.root, message, Snackbar.LENGTH_INDEFINITE)
		if (reload) {
			bar.setAction(R.string.retry) { viewModel.reload() }
		} else {
			val state = viewModel.uiState.value
			if (state != null) {
				bar.setAction(R.string.retry) {
					when (currentMediaType) {
						MediaType.BOOK -> loadNovel(state)
						MediaType.VIDEO -> loadVideo(state)
						else -> Unit
					}
				}
			}
		}
		bar.show()
	}

	private fun askForIncognitoMode() {
		buildAlertDialog(this, isCentered = true) {
			var dontAskAgain = false
			val listener = DialogInterface.OnClickListener { _, which ->
				if (which == DialogInterface.BUTTON_NEUTRAL) {
					finishAfterTransition()
				} else {
					viewModel.setIncognitoMode(which == DialogInterface.BUTTON_POSITIVE, dontAskAgain)
				}
			}
			setCheckbox(R.string.dont_ask_again, dontAskAgain) { _, isChecked ->
				dontAskAgain = isChecked
			}
			setIcon(R.drawable.ic_incognito)
			setTitle(R.string.incognito_mode)
			setMessage(R.string.incognito_mode_hint_nsfw)
			setPositiveButton(R.string.incognito, listener)
			setNegativeButton(R.string.disable, listener)
			setNeutralButton(android.R.string.cancel, listener)
			setOnCancelListener { finishAfterTransition() }
			setCancelable(true)
		}.show()
	}

	private companion object {
		const val PREFS_NAME = "media_reader"
		const val KEY_TEXT_ZOOM = "text_zoom"
		const val DEFAULT_TEXT_ZOOM = 100
		const val MIN_TEXT_ZOOM = 70
		const val MAX_TEXT_ZOOM = 200
		const val TEXT_ZOOM_STEP = 10
		val QUALITY_NUMBER = Regex("(\\d{3,4})")
	}
}
