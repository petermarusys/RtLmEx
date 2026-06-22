package com.peyo.rtlmex
 
import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.EmbedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import org.json.JSONObject
 
import kotlin.math.sqrt
 
object EmManager {
    var isInitialized = false
        private set
    var activeBackend = "None"
        private set
 
    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }
 
    private val embeddingModelPath = "/data/local/tmp/embeddinggemma-300M_seq256_mixed-precision.tflite"
    private val sentencePieceModelPath = "/data/local/tmp/sentencepiece.model"
    private var embedder: GemmaEmbeddingModel? = null
 
    data class DatabaseEntry(val text: String, val image: String?, val embedding: FloatArray)
    private val localDatabase = mutableListOf<DatabaseEntry>()
 
    var lastMatchedImage: String? = null
        private set

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        Log.i("EmManager", "Initializing EmManager")
 
        try {
            val locale = java.util.Locale.getDefault()
            val lang = locale.language.lowercase()
            val prefix = when (lang) {
                "ko" -> "ko"
                "fr" -> "fr"
                else -> "en"
            }
            val fbFile = "$prefix.fb"
            val jsonlFile = "$prefix.jsonl"
            Log.i("EmManager", "System locale: $locale. Loading database from $fbFile and $jsonlFile")

            val fbBytes = context.assets.open(fbFile).use { it.readBytes() }
            val byteBuffer = ByteBuffer.wrap(fbBytes)
            val dbEmbeddings = DatabaseEmbeddings.getRootAsDatabaseEmbeddings(byteBuffer)

            localDatabase.clear()
            var index = 0
            context.assets.open(jsonlFile).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank() && index < dbEmbeddings.embeddingsLength) {
                        val emb = dbEmbeddings.embeddings(index)
                        if (emb != null) {
                            val vector = FloatArray(emb.valuesLength)
                            for (j in 0 until emb.valuesLength) {
                                vector[j] = emb.values(j)
                            }
                            val json = JSONObject(line)
                            val answer = json.optString("answer", "")
                            val image = if (json.has("image") && !json.isNull("image")) json.getString("image") else null
                            localDatabase.add(DatabaseEntry(answer, image, vector))
                        }
                        index++
                    }
                }
            }
            Log.i("EmManager", "Loaded ${localDatabase.size} items from $jsonlFile and $fbFile")
 
            embedder = GemmaEmbeddingModel(embeddingModelPath, sentencePieceModelPath, false)
            activeBackend = "CPU"
            isInitialized = true
            Log.i("EmManager", "Embedder successfully initialized with CPU backend (memory-mapped).")
        } catch (e: Exception) {
            Log.e("EmManager", "Failed to initialize Embedder: ${e.message}", e)
        }
    }

    suspend fun ragPrompt(userQuery: String): Pair<String?, String> = withContext(Dispatchers.IO) {
        val embedderInstance = embedder
        if (embedderInstance == null) {
            return@withContext Pair(null, "Error: Embedder is not initialized.")
        }

        // --- STEP 1: EMBED THE USER QUERY ---
        val queryRequest = EmbeddingRequest.builder<String>()
            .addEmbedData(EmbedData.create(userQuery, EmbedData.TaskType.RETRIEVAL_QUERY))
            .build()
        val queryResultFuture = embedderInstance.getEmbeddings(queryRequest)
        val queryResult: List<Float>? = try {
            queryResultFuture.get()
        } catch (e: Exception) {
            Log.e("LmManager", "Failed to get embeddings for query: ${e.message}", e)
            null
        }
        val queryVector: FloatArray? = queryResult?.toFloatArray()

        if (queryVector == null) {
            return@withContext Pair(null, "Error: Could not generate embedding for query.")
        }

        // --- STEP 2: SEARCH FOR CONTEXT ---
        var bestMatch = ""
        var bestImage: String? = null
        var highestScore = -1f

        for (entry in localDatabase) {
            val similarityScore = cosineSimilarity(queryVector, entry.embedding)
            if (similarityScore > highestScore) {
                highestScore = similarityScore
                bestMatch = entry.text
                bestImage = entry.image
            }
        }
        Log.i("RagPrompt", "Highest score: $highestScore")
        if (highestScore >= 0.28) {
            lastMatchedImage = bestImage
            val ragPrompt = """
            <start_of_turn> user
              <context>
                $bestMatch
              </context>
              Answer the question based on the provided <context> above:
              Question: $userQuery  
            <end_of_turn>
            <start_of_turn> model
        """.trimIndent()
            Pair(bestImage, ragPrompt)
        } else {
            lastMatchedImage = null
            Pair(null, "Not Found")
        }
    }

    fun close() {
        isInitialized = false
        activeBackend = "None"
        embedder = null
    }
}
