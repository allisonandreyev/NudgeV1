package com.nudge.app.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** A trained model plus what we know about how well it did. */
data class SavedModel(
    val id: Int,
    val model: GestureModel,
    val accuracy: Float?,
    val perGesture: Map<String, Float>,
    val trainedAt: Long,
    /** Training-day signal levels, needed for recalibration. Null for models from older versions. */
    val reference: ChannelReference?,
    val calibratedAt: Long?
)

/** Keeps each user's latest model in app storage so it never has to be retrained just to reuse it. */
@Singleton
class ModelRepository @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val cache = MutableStateFlow<Map<String, SavedModel?>>(emptyMap())

    fun observe(username: String): Flow<SavedModel?> {
        if (username !in cache.value) {
            val loaded = load(username)
            cache.update { it + (username to loaded) }
        }
        return cache.map { it[username] }
    }

    fun save(username: String, result: TrainingResult): SavedModel = write(
        username,
        SavedModel(
            id = newId(),
            model = result.model,
            accuracy = result.accuracy,
            perGesture = result.perGesture,
            trainedAt = System.currentTimeMillis(),
            reference = result.reference,
            calibratedAt = null
        )
    )

    /** Stores a recalibrated copy under a new id, so the wearable picks it up like a new model. */
    fun saveCalibration(username: String, base: SavedModel, calibrated: GestureModel): SavedModel =
        write(username, base.copy(id = newId(base.id), model = calibrated, calibratedAt = System.currentTimeMillis()))

    private fun newId(avoid: Int = 0): Int {
        var id: Int
        do id = Random.nextInt(1, 0x10000) while (id == avoid)
        return id
    }

    private fun write(username: String, saved: SavedModel): SavedModel {
        val json = JSONObject()
            .put("id", saved.id)
            .put("classes", JSONArray(saved.model.classes))
            .put("parameters", JSONArray(saved.model.toParameters().map { it.toDouble() }))
            .put("accuracy", saved.accuracy?.toDouble() ?: JSONObject.NULL)
            .put("perGesture", JSONObject(saved.perGesture.mapValues { it.value.toDouble() }))
            .put("trainedAt", saved.trainedAt)
            .put("calibratedAt", saved.calibratedAt ?: JSONObject.NULL)
        saved.reference?.let {
            json.put("referenceRest", JSONArray(it.rest.map(Float::toDouble)))
            json.put("referenceActive", JSONArray(it.active.map(Float::toDouble)))
        }
        file(username).writeText(json.toString())
        cache.update { it + (username to saved) }
        return saved
    }

    fun delete(username: String) {
        file(username).delete()
        cache.update { it + (username to null) }
    }

    private fun load(username: String): SavedModel? {
        val f = file(username)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            val classes = json.getJSONArray("classes").let { a -> List(a.length()) { a.getString(it) } }
            val params = json.getJSONArray("parameters").let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val per = json.getJSONObject("perGesture").let { o -> o.keys().asSequence().associateWith { o.getDouble(it).toFloat() } }
            fun floats(key: String) = json.optJSONArray(key)?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val rest = floats("referenceRest")
            val active = floats("referenceActive")
            SavedModel(
                id = json.getInt("id"),
                model = GestureModel.fromParameters(params, classes),
                accuracy = if (json.isNull("accuracy")) null else json.getDouble("accuracy").toFloat(),
                perGesture = per,
                trainedAt = json.getLong("trainedAt"),
                reference = if (rest != null && active != null) ChannelReference(rest, active) else null,
                calibratedAt = if (json.isNull("calibratedAt") || !json.has("calibratedAt")) null else json.getLong("calibratedAt")
            )
        } catch (e: Exception) {
            Log.e("ModelRepository", "Couldn't read model for $username", e)
            null
        }
    }

    // Usernames are free text, so keep file names safe
    private fun file(username: String) = File(dir, username.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")
}
