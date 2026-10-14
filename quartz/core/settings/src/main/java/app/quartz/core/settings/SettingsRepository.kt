package app.quartz.core.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Typed settings in a DataStore, stored as JSON (plan §7.1). */
class SettingsRepository(context: Context) {

    private val store: DataStore<QuartzSettings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        produceFile = { File(context.filesDir, "datastore/quartz_settings.json") },
    )

    val settings: Flow<QuartzSettings> = store.data

    suspend fun update(transform: (QuartzSettings) -> QuartzSettings) {
        store.updateData(transform)
    }

    private object SettingsSerializer : Serializer<QuartzSettings> {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }

        override val defaultValue = QuartzSettings()

        override suspend fun readFrom(input: InputStream): QuartzSettings =
            try {
                json.decodeFromString(QuartzSettings.serializer(), input.readBytes().decodeToString())
            } catch (e: SerializationException) {
                throw CorruptionException("Unreadable settings", e)
            } catch (e: IllegalArgumentException) {
                throw CorruptionException("Unreadable settings", e)
            }

        override suspend fun writeTo(t: QuartzSettings, output: OutputStream) {
            output.write(json.encodeToString(QuartzSettings.serializer(), t).encodeToByteArray())
        }
    }
}
