package com.peyo.rtlmex

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import com.peyo.rtlmex.ui.theme.RtLmExTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Message(
    val isUser: Boolean,
    val content: String,
    val imagePath: String? = null
)


@Composable
fun MessageItem(message: Message) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.imagePath != null && message.imagePath.isNotEmpty()) {
            val bitmap = remember(message.imagePath) {
                try {
                    context.assets.open(message.imagePath).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .wrapContentWidth()
                )
            }
        } else {
            MarkdownText(text = message.content)
        }
    }
}


class MainActivity : ComponentActivity() {
    private val messagesState = mutableStateOf<List<Message>>(emptyList())
    val messages: List<Message>
        get() = messagesState.value

    fun addMessage(message: Message) {
        messagesState.value = messagesState.value + message
    }

    fun updateLastMessage(updated: Message) {
        val list = messagesState.value.toMutableList()
        if (list.isNotEmpty()) {
            list[list.lastIndex] = updated
            messagesState.value = list
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RtLmExTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }

    @Composable
    fun ChatScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Use messages to keep UI in sync with backend
        val messages = messages
        var textInput by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        // Trigger initialization on launch
        LaunchedEffect(Unit) {
            if (!EmManager.isInitialized) {
                try {
                    withContext(Dispatchers.IO) {
                        EmManager.initialize(context)
                    }
                } catch (e: Exception) {
                    Log.e("Chat", "Em Initialization Error", e)
                }
            }
            if (!LmManager.isInitialized) {
                try {
                    withContext(Dispatchers.IO) {
                        LmManager.initialize(context)
                    }
                } catch (e: Exception) {
                    Log.e("Chat", "Lm Initialization Error", e)
                }
            }
        }

        // Automatically scroll to the last item
        val lastMessageText = messages.lastOrNull()?.content
        LaunchedEffect(messages.size, lastMessageText) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1, 10000)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                EmManager.close()
                LmManager.close()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(messages) { message ->
                    MessageItem(message)
                }
            }

            // Bottom text input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        scope.launch {
                                            listState.animateScrollBy(-500f)
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        scope.launch {
                                            listState.animateScrollBy(500f)
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            val prompt = textInput
                            textInput = ""
                            sendMessage(prompt, scope)
                        }
                    }),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            val prompt = textInput
                            textInput = ""
                            sendMessage(prompt, scope)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    private fun sendMessage(
        prompt: String,
        scope: CoroutineScope,
    ) {
        if (prompt.isBlank()) return

        addMessage(Message(true, prompt.trim()))

        scope.launch {
            var image: String
            var ragPrompt: String

            withContext(Dispatchers.IO) {
                val result = EmManager.ragPrompt(prompt.trim())
                image = result.first ?: ""
                ragPrompt = result.second
                Log.i("RagPrompt", ragPrompt)
            }

            if (ragPrompt == "Not Found") {
                addMessage(Message(false, "I don't know"))
            } else {
                if (image.isNotEmpty()) {
                    addMessage(Message(false, "", image))
                }
                addMessage(Message(false, "..."))
                try {
                    val flow = LmManager.sendMessageAsync(ragPrompt.trim())
                    if (flow != null) {
                        withContext(Dispatchers.IO) {
                            var accumulatedText = ""
                            var tokenCount = 0
                            val startTime = System.currentTimeMillis()
                            var lastLogTime = startTime

                            flow.collect { message ->
                                val chunk = message.contents.toString()
                                accumulatedText += chunk
                                tokenCount++

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastLogTime >= 3000) {
                                    val elapsedSeconds = (currentTime - startTime) / 1000.0
                                    val rate =
                                        if (elapsedSeconds > 0) tokenCount / elapsedSeconds else 0.0
                                    Log.i(
                                        "TokenRate",
                                        "Current token rate: %.2f tokens/sec (tokens: %d, time: %.2fs)".format(
                                            rate,
                                            tokenCount,
                                            elapsedSeconds
                                        )
                                    )
                                    lastLogTime = currentTime
                                }

                                withContext(Dispatchers.Main) {
                                    updateLastMessage(Message(false, accumulatedText))
                                }
                            }

                            // Print final rates
                            val endTime = System.currentTimeMillis()
                            val totalElapsedSeconds = (endTime - startTime) / 1000.0
                            val finalRate =
                                if (totalElapsedSeconds > 0) tokenCount / totalElapsedSeconds else 0.0
                            Log.i(
                                "TokenRate",
                                "Final token rate: %.2f tokens/sec (total tokens: %d, total time: %.2fs)".format(
                                    finalRate,
                                    tokenCount,
                                    totalElapsedSeconds
                                )
                            )
                        }
                    } else {
                        updateLastMessage(Message(false, "Error: Conversation not initialized"))
                    }
                } catch (e: Exception) {
                    Log.e("Chat", "Model Error", e)
                    updateLastMessage(Message(false, "Error: ${e.message}"))
                }
            }
        }
    }
}
