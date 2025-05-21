# Kuick Core

`kuick-core` provides essential building blocks and utilities that form the foundation for application development within the Kuick ecosystem. It offers a set of common abstractions and implementations for everyday tasks, promoting consistency and reducing boilerplate code.

## Key Features

Here are some of the core features provided by `kuick-core`:

### Repositories

Repositories in Kuick provide an abstraction layer for data access, making it easier to manage and interact with your data sources.

*   **`Repository<I : Any, T : Any>`**: A generic interface for basic CRUD (Create, Read, Update, Delete) operations on entities of type `T` with identifiers of type `I`.
*   **`ModelRepository<I : Any, T : Model<I>>`**: A specialized repository for entities that implement the `Model<I>` interface (which typically means they have an `id` property).

**Example:**

```kotlin
import com.kuick.core.db.ModelRepository
import com.kuick.core.db.Repository
import com.kuick.models.Id
import com.kuick.models.Model

// 1. Define a simple data model
data class User(override val id: Id<User> = Id(), val name: String, val email: String) : Model<Id<User>>

// 2. Define a repository for the User model
interface UserRepository : ModelRepository<Id<User>, User> {
    suspend fun findByEmail(email: String): User?
}

// 3. Example of using a (hypothetical) UserRepository implementation
suspend fun demonstrateRepositories(userRepo: UserRepository) {
    // Insert a new user
    val newUser = User(name = "Alice", email = "alice@example.com")
    val insertedUser = userRepo.insert(newUser)
    println("Inserted user: $insertedUser")

    // Find a user by ID
    val foundUser = userRepo.findById(insertedUser.id)
    println("Found user by ID: $foundUser")

    // Find a user by email (custom method)
    val userByEmail = userRepo.findByEmail("alice@example.com")
    println("Found user by email: $userByEmail")
}
```

### Caching

`kuick-core` provides a simple yet effective caching mechanism to improve performance by storing and reusing the results of expensive operations.

*   **`Cache<K : Any, V : Any>`**: An interface defining basic cache operations like `get`, `set`, and `invalidate`.
*   **`CacheManager`**: A class that manages different cache instances and provides convenient extension functions like `cached` for easy memoization.

**Example:**

```kotlin
import com.kuick.core.cache.CacheManager
import com.kuick.core.cache.GuavaCache // Example implementation
import kotlinx.coroutines.delay

suspend fun demonstrateCaching(cacheManager: CacheManager) {
    // Example of a function whose result we want to cache
    suspend fun expensiveOperation(key: String): String {
        println("Performing expensive operation for key: $key")
        delay(1000) // Simulate work
        return "Result for $key"
    }

    // Using the 'cached' function
    // The first call will execute expensiveOperation
    val result1 = cacheManager.cached("myCache", "dataKey1") {
        expensiveOperation("dataKey1")
    }
    println(result1)

    // The second call with the same key will return the cached result
    val result2 = cacheManager.cached("myCache", "dataKey1") {
        expensiveOperation("dataKey1") // This won't be printed again
    }
    println(result2)

    // Different key, will execute the operation again
    val result3 = cacheManager.cached("myCache", "dataKey2") {
        expensiveOperation("dataKey2")
    }
    println(result3)
}

// To run the example, you'd need to initialize CacheManager, e.g.:
// val cacheManager = CacheManager()
// cacheManager.getCache<String, String>("myCache") { GuavaCache() } // Or any other Cache impl
// demonstrateCaching(cacheManager)
```

### Event Bus

The `Bus` interface provides a simple publish-subscribe mechanism for decoupled communication between different parts of an application.

*   **`Bus`**: An interface with `publish` and `subscribe` methods. Events are published to the bus, and interested subscribers react to them.

**Example:**

```kotlin
import com.kuick.core.bus.Bus
import com.kuick.core.bus.InmemoryBus // Example implementation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// 1. Define an event class
data class MessageEvent(val message: String)

suspend fun demonstrateEventBus() {
    val eventBus: Bus = InmemoryBus() // Use an actual implementation

    // 2. Subscribe to an event
    eventBus.subscribe(MessageEvent::class) { event ->
        println("Received event: ${event.message}")
    }

    // 3. Publish an event
    println("Publishing event...")
    eventBus.publish(MessageEvent("Hello from the Event Bus!"))

    // Give some time for the event to be processed in async scenarios
    kotlinx.coroutines.delay(100)
}
```

### Logging

`kuick-core` includes a simple logging facade that allows you to integrate with various logging implementations.

*   **`Logger`**: An interface for logging messages at different levels (trace, debug, info, warn, error).
*   Configuration is typically handled by the underlying logging framework chosen (e.g., Logback, SLF4J). `StaticLoggerContext` allows for basic static configuration.

**Example:**

```kotlin
import com.kuick.core.logger.Level
import com.kuick.core.logger.LogRecord
import com.kuick.core.logger.Logger
import com.kuick.core.logger.LoggingContext // For potential context setup
import com.kuick.core.logger.format.PatternLogFormatter // Example formatter
import com.kuick.core.logger.appender.ConsoleAppender // Example appender
import com.kuick.core.logger.config.LoggerConfig
import com.kuick.core.logger.config.LoggingConfiguration
import com.kuick.core.logger.getLogger

// Basic setup (in a real app, this might be more sophisticated or auto-configured)
object MyLoggerConfig {
    fun setup() {
        val config = LoggingConfiguration().apply {
            addAppender(ConsoleAppender(PatternLogFormatter()))
            addLogger(LoggerConfig("", Level.INFO)) // Default root logger level
            addLogger(LoggerConfig("com.example", Level.DEBUG))
        }
        // In a real app, you'd apply this configuration to your logging system.
        // For kuick-core's basic logger, this might involve StaticLoggerContext or similar.
        // As kuick-core is a facade, the exact setup depends on the chosen implementation.
        // For this example, we'll assume getLogger() provides a working logger.
    }
}


fun demonstrateLogging() {
    // MyLoggerConfig.setup() // Call setup if needed by your logging backend

    // Get a logger instance (typically by class or name)
    val logger: Logger = getLogger("MyApplication") // Or getLogger(MyApplication::class.java)

    logger.trace { "This is a trace message." } // Might not be visible if level is INFO
    logger.debug { "This is a debug message." } // Might not be visible if level is INFO
    logger.info("This is an info message.")
    logger.warn("This is a warning message.", RuntimeException("Something to warn about"))
    logger.error("This is an error message.", IllegalStateException("Critical failure!"))
}
```

### Other Utilities

`kuick-core` also bundles several other useful utilities:

*   **`kuick.models`**:
    *   `Id<T>`: A type-safe wrapper for entity identifiers, enhancing clarity and preventing accidental mixing of IDs from different models.
    *   `Email`: A value class for representing email addresses, potentially with validation.
    *   `Password`: A class for handling passwords, often with hashing capabilities (though hashing itself might be in a security-focused module).
    *   `Named`: An interface for objects that have a `name` property.
*   **`kuick.time`**:
    *   `TimeProvider`: An interface for abstracting time, useful for testing.
    *   `now`, `nowInstant`, `nowUnix`, `nowMillis`: Functions to get the current time in various formats.
    *   Extensions for `kotlin.time.Duration`.
*   **`kuick.utils`**:
    *   `randomUUID()`: Generates a random UUID string.
    *   String utilities (e.g., `capitalize`, `uncapitalize`, `camelCase`, `snakeCase`).
    *   Collection utilities and other miscellaneous helper functions.

**Example of using some utilities:**

```kotlin
import com.kuick.models.Email
import com.kuick.models.Id
import com.kuick.time.nowUnix
import com.kuick.utils.randomUUID

fun demonstrateUtilities() {
    // IDs
    val userId: Id<User> = Id("user-123") // Assuming User is defined as in Repository example
    println("User ID: $userId")

    // Email
    try {
        val email = Email("test@example.com")
        println("Valid Email: $email")
    } catch (e: IllegalArgumentException) {
        println("Invalid Email: ${e.message}")
    }


    // Time
    val currentUnixTime = nowUnix()
    println("Current Unix Timestamp: $currentUnixTime")

    // UUID
    val uniqueId = randomUUID()
    println("Generated UUID: $uniqueId")
}
```

## How These Components Work Together

The components in `kuick-core` are designed to be modular yet complementary:

*   **Repositories** manage your data models (which might use `Id` from `kuick.models`).
*   Expensive repository calls or business logic can be **cached** using the `CacheManager` to improve performance.
*   **Events** from the `Bus` can be used to decouple actions, for instance, publishing an event after a user is created via a repository, allowing other parts of the system to react without direct coupling.
*   Throughout your application, consistent **logging** helps in monitoring and debugging.
*   Various **utilities** simplify common tasks like generating unique identifiers, handling time, or manipulating strings.

By providing these fundamental pieces, `kuick-core` helps you build robust and maintainable applications more efficiently. You can pick and choose the components you need and integrate them with other Kuick libraries or third-party solutions.
