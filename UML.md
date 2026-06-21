# UML Diagrams

This document outlines the architecture and execution flow of the Android application using UML class and sequence diagrams.

---

## 1. Class Diagram

The following class diagram shows the structure of the application, including UI components, singleton managers, FlatBuffers models, and their relationships.

```mermaid
classDiagram
    direction TB

    %% MainActivity & UI Components
    class MainActivity {
        -messagesState: MutableState~List-Message~
        +messages: List~Message~
        +addMessage(message: Message)
        +updateLastMessage(updated: Message)
        #onCreate(savedInstanceState: Bundle?)
        +ChatScreen()
        -sendMessage(prompt: String, scope: CoroutineScope)
    }

    class Message {
        <<data>>
        +isUser: Boolean
        +content: String
    }

    class MessageItem {
        <<Composable>>
        +MessageItem(message: Message)
    }

    class MarkdownText {
        <<Composable>>
        +MarkdownText(text: String, modifier: Modifier)
    }

    %% Markdown Parser Models
    class MarkdownBlock {
        <<interface>>
    }
    class Header {
        <<data>>
        +level: Int
        +content: AnnotatedString
    }
    class CodeBlock {
        <<data>>
        +language: String?
        +code: String
    }
    class BulletList {
        <<data>>
        +items: List~AnnotatedString~
    }
    class Paragraph {
        <<data>>
        +content: AnnotatedString
    }

    %% Managers (Singletons)
    class EmManager {
        <<singleton>>
        -embeddingModelPath: String
        -sentencePieceModelPath: String
        -embedder: GemmaEmbeddingModel?
        -localDatabase: MutableList~DatabaseEntry~
        +isInitialized: Boolean
        +activeBackend: String
        -cosineSimilarity(v1: FloatArray, v2: FloatArray) Float
        +initialize(context: Context)
        +ragPrompt(userQuery: String) String
        +close()
    }

    class DatabaseEntry {
        <<data>>
        +text: String
        +embedding: FloatArray
    }

    class LmManager {
        <<singleton>>
        -engine: Engine?
        -conversation: Conversation?
        +isInitialized: Boolean
        +activeBackend: String
        +initialize(context: Context)
        +sendMessageAsync(prompt: String) Flow~Message~
        +close()
    }

    %% FlatBuffers Classes
    class DatabaseEmbeddings {
        +embeddingsLength: Int
        +embeddings(j: Int) Embedding?
        +embeddings(obj: Embedding, j: Int) Embedding?
        +__init(_i: Int, _bb: ByteBuffer)
        +__assign(_i: Int, _bb: ByteBuffer) DatabaseEmbeddings
        +getRootAsDatabaseEmbeddings(_bb: ByteBuffer) DatabaseEmbeddings
    }

    class Embedding {
        +valuesLength: Int
        +values(j: Int) Float
        +__init(_i: Int, _bb: ByteBuffer)
        +__assign(_i: Int, _bb: ByteBuffer) Embedding
    }

    %% Inheritance / Implementations
    MainActivity --|> ComponentActivity
    DatabaseEmbeddings --|> Table
    Embedding --|> Table
    Header ..|> MarkdownBlock
    CodeBlock ..|> MarkdownBlock
    BulletList ..|> MarkdownBlock
    Paragraph ..|> MarkdownBlock

    %% Associations & Dependencies
    MainActivity ..> Message : "manages list of"
    MainActivity ..> MessageItem : "renders"
    MainActivity ..> EmManager : "initializes and requests context from"
    MainActivity ..> LmManager : "initializes and streams responses from"
    MessageItem ..> MarkdownText : "renders content using"
    MarkdownText ..> MarkdownBlock : "parses markdown text into blocks"
    EmManager *-- DatabaseEntry : "defines"
    EmManager ..> DatabaseEmbeddings : "parses ko.fb with"
    DatabaseEmbeddings --> Embedding : "references lists of"
```

---

## 2. Sequence Diagram

The following sequence diagram outlines the asynchronous communication and information flow during application initialization and message processing.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant MA as MainActivity (UI)
    participant EM as EmManager (Embedding)
    participant LM as LmManager (Language Model)
    participant GEM as GemmaEmbeddingModel (LiteRT)
    participant LME as Engine / Conversation (LiteRT-LM)

    %% Scenario 1: App Launch / Initialization
    Note over User, LME: Phase 1: Initialization on App Launch
    MA->>EM: initialize(context) (IO Thread)
    activate EM
    EM->>EM: Read assets/ko.fb & assets/ko.txt
    EM->>EM: Extract text after [Answer] & parse embeddings
    EM->>GEM: Instantiate (embeddinggemma-300M_seq256)
    EM-->>MA: Success / Initialized
    deactivate EM

    MA->>LM: initialize(context) (IO Thread)
    activate LM
    LM->>LME: Instantiate Engine & Create Conversation
    LM-->>MA: Success / Initialized
    deactivate LM

    %% Scenario 2: Message Flow
    Note over User, LME: Phase 2: Message Processing & RAG Pipeline
    User->>MA: Types text & clicks "Send"
    MA->>MA: addMessage(User message)
    MA->>MA: addMessage(Placeholder "...")

    MA->>EM: ragPrompt(userQuery) (IO Thread)
    activate EM
    EM->>GEM: getEmbeddings(userQuery)
    activate GEM
    GEM-->>EM: queryVector (FloatArray)
    deactivate GEM
    
    EM->>EM: Cosine Similarity match against localDatabase
    alt Highest Score >= 0.28
        EM-->>MA: Returns formatted RAG Prompt with matched context
    else Highest Score < 0.28
        EM-->>MA: Returns "Not Found"
    end
    deactivate EM

    alt RAG Prompt is "Not Found"
        MA->>MA: updateLastMessage("I don't know")
    else RAG Prompt is valid
        MA->>LM: sendMessageAsync(ragPrompt)
        activate LM
        LM->>LME: sendMessageAsync(ragPrompt)
        activate LME
        LME-->>LM: Flow<Message> (stream of tokens)
        deactivate LME
        LM-->>MA: Flow<Message>
        deactivate LM
        
        loop Collect Stream Tokens
            MA->>MA: Append token to accumulatedText
            MA->>MA: updateLastMessage(accumulatedText) (Main Thread UI update)
        end
    end
```

---

## 3. Class Components Overview

- **[MainActivity]**: The entry point and single activity of the Android application. It manages the Compose UI state, handles user text input, and initiates the RAG pipeline flow.
- **[EmManager]**: The local vector database manager. It handles memory-mapped loading of pre-calculated FlatBuffers embeddings (`ko.fb`) and answers (`ko.txt`), calculates similarity on the CPU, and manages the local query embedder.
- **[LmManager]**: The Language Model engine wrapper. It handles model initialization, local storage configurations, and manages model chats asynchronously via Flows.
- **[DatabaseEmbeddings]**: Generated FlatBuffers model classes used to read vectorized float arrays directly from asset memory.
- **[MarkdownText]**: Custom rendering utility that parses markdown syntaxes (headers, paragraphs, bold, code-blocks) to Jetpack Compose elements.
