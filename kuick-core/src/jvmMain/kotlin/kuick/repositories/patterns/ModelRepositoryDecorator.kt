package kuick.repositories.patterns

import kuick.repositories.ModelRepository

/**
 * [ModelRepository]
 */
open class ModelRepositoryDecorator<I: Any, T: Any>(private val repo: ModelRepository<I, T>): ModelRepository<I, T> by repo
