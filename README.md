[![Build Status](https://github.com/gokoan/kuick/workflows/CI+with+Gradle/badge.svg)](https://travis-ci.org/NoSpoonLab/kuick)
# Kuick Libraries

Kuick is a collection of Kotlin multiplatform libraries designed to accelerate application development by providing robust, reusable components for common tasks.

## Overview

The Kuick project is organized into several submodules, each targeting a specific area of functionality. These libraries aim to be lightweight, easy to use, and extensible.

## Submodules

Here's a brief overview of the available submodules:

*   **[`kuick-core/`](kuick-core/README.md)**: The core module providing essential building blocks such as:
    *   A flexible repository pattern for data persistence.
    *   Caching mechanisms.
    *   An event bus system.
    *   Logging utilities.
    *   Various other utilities for models, time, and more.
    *   [Read more...](kuick-core/README.md)

*   **[`kuick-api-rpc/`](kuick-api-rpc/README.md)**: Facilitates RPC-style (Remote Procedure Call) communication.
    *   Provides tools to define remote API contracts and clients to consume them.
    *   [Read more...](kuick-api-rpc/README.md)

*   **[`kuick-repositories-jasync-sql/`](kuick-repositories-jasync-sql/README.md)**: A JVM-specific implementation of the `kuick-core` repository pattern.
    *   Uses [JAsync SQL](https://github.com/jasync-sql/jasync-sql) for asynchronous database access, primarily targeting PostgreSQL.
    *   [Read more...](kuick-repositories-jasync-sql/README.md)

## Getting Started

To use a Kuick library in your project, you'll typically add it as a dependency in your `build.gradle` or `build.gradle.kts` file. Please refer to the individual submodule READMEs for specific setup instructions and usage examples.

## Contributing

Contributions are welcome! If you'd like to contribute, please fork the repository and submit a pull request.
