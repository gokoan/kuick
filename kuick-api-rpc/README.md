# Kuick API RPC

This submodule provides tools and abstractions for facilitating RPC-style communication between different parts of an application or between different services.

## Overview

The main components of this submodule are:

*   **`RpcClient`**: An interface that defines the contract for sending RPC requests and receiving responses. Concrete implementations of this interface handle the actual communication mechanism (e.g., HTTP, WebSockets).
*   **`AbstractRemoteApi`**: An abstract class that simplifies the creation of RPC client proxies. It uses an `RpcClient` to perform remote calls and handles the serialization/deserialization of data.

## Usage Example

Here's a simple example of how to use these components:

1.  **Define a service interface**:

    ```kotlin
    interface MyService {
        suspend fun greet(name: String): String
        suspend fun add(a: Int, b: Int): Int
    }
    ```

2.  **Create a client class**:

    This class extends `AbstractRemoteApi` and implements your service interface.

    ```kotlin
    import com.kuick.api.rpc.AbstractRemoteApi
    import com.kuick.api.rpc.RpcClient

    class MyServiceClient(rpcClient: RpcClient) : AbstractRemoteApi(rpcClient), MyService {
        override suspend fun greet(name: String): String {
            return call("greet", name)
        }

        override suspend fun add(a: Int, b: Int): Int {
            return call("add", a, b)
        }
    }
    ```

3.  **Instantiate and use the client**:

    You'll need a concrete implementation of `RpcClient`. For this example, let's assume you have one called `HttpRpcClient`.

    ```kotlin
    import com.kuick.api.rpc.RpcClient // Assuming HttpRpcClient is in this package or similar

    suspend fun main() {
        // Replace with your actual RpcClient implementation
        val rpcClient: RpcClient = HttpRpcClient("http://your-service-url/api") 

        val myServiceClient = MyServiceClient(rpcClient)

        try {
            val greeting = myServiceClient.greet("World")
            println("Server says: $greeting") // Expected: Server says: Hello, World! (or similar)

            val sum = myServiceClient.add(5, 3)
            println("5 + 3 = $sum") // Expected: 5 + 3 = 8
        } catch (e: Exception) {
            println("Error during RPC call: ${e.message}")
        }
    }
    ```

## Integration with other Kuick Libraries

This `kuick-api-rpc` submodule provides the core abstractions for RPC. It is often used in conjunction with other Kuick libraries that offer concrete `RpcClient` implementations, such as:

*   `kuick-api-rpc-ktor-client`: For Ktor-based HTTP clients.
*   Other libraries providing clients for different protocols or frameworks.

By using these libraries together, you can easily set up RPC communication in your Kuick applications.
