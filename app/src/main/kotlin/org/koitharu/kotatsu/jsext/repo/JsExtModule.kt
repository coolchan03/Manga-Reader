package org.koitharu.kotatsu.jsext.repo

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Own module, so the feature stays out of core/AppModule.kt. */
@Module
@InstallIn(SingletonComponent::class)
object JsExtModule {

	@Provides
	@Singleton
	fun provideJsSourceStore(@ApplicationContext context: Context): JsSourceStore =
		JsSourceStore(File(context.filesDir, "js_sources"))
}
