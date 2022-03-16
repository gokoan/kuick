package kuick.annotations

/**
 * Use when the interface is in common, but the implementation is on jvm, and the package or naming convention doesn't match
 */
annotation class StrImplementation(val fqname: String)
