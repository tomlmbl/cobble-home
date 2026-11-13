package io.github.tomlmbl.launcher.settings

import android.content.Context
import android.util.Log
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.context.DockApp
import io.github.tomlmbl.launcher.context.Profile
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Configuration persisted in a single typed file. Logs no name or identifier, only counts and positions. */
class ConfigStore(context: Context, private val scope: CoroutineScope) {

    private val store = DataStoreFactory.create(
        serializer = ConfigSerializer(KeystoreCodec()),
        scope = scope,
        produceFile = { File(context.filesDir, FILE_NAME) },
    )

    /** First read, logged: counts only. */
    @Volatile private var read = false

    /** null until the file has been read, so an empty Home is never shown by mistake. */
    val config: StateFlow<Config?> = store.data
        .onEach { if (!read) { read = true; Log.i(TAG, "read ${ConfigChange.summary(it)}") } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * [source] names the write site: a write that changes favorites or a dock is logged with it, as positions and
     * counts only ([ConfigChange]).
     */
    fun update(source: String, transform: (Config) -> Config) {
        scope.launch {
            store.updateData { before ->
                transform(before).also { after -> ConfigChange.describe(before, after)?.let { Log.i(TAG, "$source $it") } }
            }
        }
    }

    /** Applies a profile selection; returns the added profiles without a kept dock. */
    fun selectProfiles(choice: List<Profile>): List<Profile> {
        val before = config.value ?: Config()
        val after = before.selectProfiles(choice)
        update("selectProfiles") { it.selectProfiles(choice) }
        return after.profiles.needingApps(before.profiles)
    }

    fun setDockApps(profile: Profile, apps: List<App>) {
        update("setDockApps") { it.withDockApps(profile, apps.map { app -> DockApp(app.id, app.label) }) }
    }

    private class ConfigSerializer(private val codec: SecretCodec) : Serializer<Config> {
        override val defaultValue = Config()
        override suspend fun readFrom(input: InputStream) = ConfigFormat.decode(input.readBytes().decodeToString(), codec)
        override suspend fun writeTo(t: Config, output: OutputStream) = output.write(ConfigFormat.encode(t, codec).encodeToByteArray())
    }

    private companion object {
        const val FILE_NAME = "launcher.conf"
        const val TAG = "Config"
    }
}
