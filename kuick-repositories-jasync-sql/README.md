# Kuick Repositories JAsync SQL

`kuick-repositories-jasync-sql` provides a concrete implementation of the `kuick-core` repository pattern, leveraging the JAsync SQL library for asynchronous database access. This module is primarily designed for interacting with PostgreSQL databases in a non-blocking manner.

## Key Features

*   **Integration with `kuick-core`**: Seamlessly implements the `Repository<I, T>` and `ModelRepository<I, T>` interfaces from `kuick-core`.
*   **Asynchronous Operations**: Utilizes JAsync SQL for fully asynchronous and non-blocking database calls, making it suitable for high-performance applications.
*   **Connection Pooling**: Manages database connections efficiently using `JasyncPool`, which is a wrapper around JAsync's connection pool.
*   **Environment-based Configuration**: `JasyncPool` can be easily configured using environment variables for database connection details (host, port, user, password, database name).

## Dependencies

This submodule relies on:

*   `kuick-core`: For the core repository interfaces and models.
*   `jasync-sql-postgresql`: The JAsync driver for PostgreSQL.

## Usage Example

Here's how you can use `kuick-repositories-jasync-sql` to interact with a PostgreSQL database:

1.  **Define your data model**:

    This model should typically implement `Model<I>` from `kuick-core`.

    ```kotlin
    import com.kuick.models.Id
    import com.kuick.models.Model
    import java.time.LocalDateTime

    // Example: A simple 'Product' model
    data class Product(
        override val id: Id<Product> = Id(), // Auto-generated ID
        val name: String,
        val description: String?,
        val price: Double,
        val createdAt: LocalDateTime = LocalDateTime.now()
    ) : Model<Id<Product>>
    ```

2.  **Set up environment variables for `JasyncPool`**:

    Before running your application, ensure these environment variables are set:

    ```bash
    export DB_HOST=localhost
    export DB_PORT=5432
    export DB_USER=youruser
    export DB_PASSWORD=yourpassword
    export DB_NAME=yourdatabase
    # Optional:
    # export DB_POOL_MAX_ACTIVE_CONNECTIONS=10
    # export DB_POOL_MAX_IDLE_TIME_MS=600000
    # export DB_POOL_MAX_QUEUE_SIZE=100
    ```

3.  **Instantiate `JasyncPool` and `ModelRepositoryJasync`**:

    ```kotlin
    import com.kuick.core.db.ModelRepository
    import com.kuick.repositories.jasyncsql.JasyncPool
    import com.kuick.repositories.jasyncsql.ModelRepositoryJasync
    import kotlinx.coroutines.runBlocking

    // Placeholder for actual table definition and schema management
    // In a real application, you would use a migration tool or ensure the table exists.
    const val PRODUCTS_TABLE_NAME = "products" // Or your actual table name

    suspend fun main() {
        // 1. Create JasyncPool from environment variables
        val pool = JasyncPool.fromEnvironment()

        // 2. Create a ModelRepository for the Product model
        //    Requires the table name and the KClass of the model.
        val productRepository: ModelRepository<Id<Product>, Product> =
            ModelRepositoryJasync(Product::class, pool.pool(), PRODUCTS_TABLE_NAME)

        // 3. Perform database operations
        try {
            // Example: Insert a new product
            val newProduct = Product(name = "Awesome Laptop", description = "High-performance laptop", price = 1200.99)
            val insertedProduct = productRepository.insert(newProduct)
            println("Inserted product: $insertedProduct")

            // Example: Find the product by ID
            val foundProduct = productRepository.findById(insertedProduct.id)
            if (foundProduct != null) {
                println("Found product by ID: $foundProduct")
            } else {
                println("Product with ID ${insertedProduct.id} not found.")
            }

            // Example: Get all products (use with caution on large tables)
            val allProducts = productRepository.getAll()
            println("All products (${allProducts.size}):")
            allProducts.forEach { println("- $it") }

        } catch (e: Exception) {
            System.err.println("Database operation failed: ${e.message}")
            e.printStackTrace()
        } finally {
            // Close the connection pool when the application shuts down
            pool.close()
            println("Connection pool closed.")
        }
    }

    // To run this example (ensure PostgreSQL is running and configured):
    // fun main() = runBlocking { main() }
    ```

    **Note on Table Schema**: `ModelRepositoryJasync` expects the corresponding database table to exist. You'll need to manage your database schema (e.g., using SQL migration tools like Flyway or Liquibase) to create tables like `products` with appropriate columns matching your model (`id`, `name`, `description`, `price`, `createdAt`).

## JVM Specific

Please note that `kuick-repositories-jasync-sql` is a JVM-specific module because JAsync SQL is a Java library. It is not suitable for Kotlin Multiplatform projects targeting non-JVM platforms.

This module is ideal for backend services and applications running on the JVM that require efficient, asynchronous communication with PostgreSQL.
