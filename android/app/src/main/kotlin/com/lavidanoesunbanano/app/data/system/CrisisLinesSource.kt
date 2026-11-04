package com.lavidanoesunbanano.app.data.system

import android.content.Context
import com.lavidanoesunbanano.app.domain.excuse.CrisisLine
import com.lavidanoesunbanano.app.domain.excuse.CrisisLines
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vive en el APK: las líneas de ayuda tienen que verse sin internet. */
class CrisisLinesSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun lines(): List<CrisisLine> = withContext(Dispatchers.IO) {
        CrisisLines.visible(context.assets.open(FILE).bufferedReader().use { it.readText() })
    }

    private companion object {
        const val FILE = "crisis_lines.json"
    }
}
