package kuick.models

import kuick.annotations.StrImplementation


interface Id { val id: String }

@StrImplementation("kuick.models.IdProviderJvm")
interface IdProvider {

    fun randomId(): String
}

abstract class AbstractId(override val id: String) : kuick.models.Id {
    override fun equals(other: Any?): Boolean = (other is AbstractId) && this.id == other.id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id
}
