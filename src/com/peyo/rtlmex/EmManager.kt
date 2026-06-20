package com.peyo.rtlmex

import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.EmbedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val localDatabase = listOf(
        "The internal IP address for the testing server is 192.168.1.100.",
        "Employee expense reports are due on the last Friday of the month.",
        "The office WiFi password is 'GemmaLocal2026'."
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            embedder = GemmaEmbeddingModel(embeddingModelPath, sentencePieceModelPath, false)
            activeBackend = "CPU"
            isInitialized = true
            Log.i("EmManager", "Embedder successfully initialized with CPU backend (memory-mapped).")
        } catch (e: Exception) {
            Log.e("EmManager", "Failed to initialize Embedder: ${e.message}", e)
        }
    }

    suspend fun ragPrompt(userQuery: String): String = withContext(Dispatchers.IO) {
        val embedderInstance = embedder
        if (embedderInstance == null) {
            return@withContext "Error: Embedder is not initialized."
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
            return@withContext "Error: Could not generate embedding for query."
        }

        // --- STEP 2: SEARCH FOR CONTEXT ---
        var bestMatch = ""
        var highestScore = -1f

        for (doc in localDatabase) {
            val docRequest = EmbeddingRequest.builder<String>()
                .addEmbedData(EmbedData.create(doc, EmbedData.TaskType.RETRIEVAL_DOCUMENT))
                .build()
            val docResultFuture = embedderInstance.getEmbeddings(docRequest)
            val docResult: List<Float>? = try {
                docResultFuture.get()
            } catch (e: Exception) {
                Log.e("EmManager", "Failed to get embeddings for doc: ${e.message}", e)
                null
            }
            val docVector: FloatArray? = docResult?.toFloatArray()
            if (docVector != null) {
                val similarityScore = cosineSimilarity(queryVector, docVector)

                if (similarityScore > highestScore) {
                    highestScore = similarityScore
                    bestMatch = doc
                }
            }
        }
        Log.i("Rag", "Highest score: $highestScore")
        // --- STEP 3: GENERATE THE ANSWER ---
        val ragPrompt = """
            Answer the question using ONLY the provided context. 
            If the answer is not in the context, say "I don't know".
            
            Context: ${if (highestScore >= 0.2) bestMatch else "None found."}
            Question: $userQuery
            Answer:
        """.trimIndent()

        // Generate response via LiteRT-LM
        ragPrompt
    }

    fun close() {
        isInitialized = false
        activeBackend = "None"
        embedder = null
    }
}
