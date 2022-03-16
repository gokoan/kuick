package kuick.di

import com.google.inject.*
import com.google.inject.spi.InjectionPoint
import javassist.ClassPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kuick.annotations.StrImplementation
import kuick.core.KuickInternal
import kuick.utils.WeakProperty
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

fun Guice(callback: Binder.() -> Unit): Injector = createGuiceInjectorEx(object : Module {
    override fun configure(binder: Binder) = callback(binder)
})

fun getValidClassOrNull(vararg names: String): Class<*>? {
    for (name in names) {
        try {
            return Class.forName(name)
        } catch (_: ClassNotFoundException) {
        }
    }
    return null
}

fun getServiceClass(clazz: Class<*>): Class<*>? {
    val baseName = clazz.canonicalName
    return getValidClassOrNull(
        "${baseName}Service",
        "${baseName.removeSuffix("Api")}Service",
        "${baseName.removeSuffix("Internal")}ApiService",
        "${baseName.removeSuffix("Internal")}Service",
    )
}

object GuiceEx {
    @JvmStatic
    fun transformConstructor(key: Key<*>, constructorInjector: InjectionPoint?): InjectionPoint? {
        //println("GuiceEx.transformConstructor: $key, $constructorInjector")
        val rawType = key.typeLiteral.rawType
        if (constructorInjector == null && rawType.isInterface) {
            val strImpl = rawType.getAnnotation(StrImplementation::class.java)
            val serviceClass = strImpl?.fqname?.let { getValidClassOrNull(it) }
                ?: getServiceClass(rawType)
            //println("!! INTERFACE !! $rawType -> $serviceClass")
            if (serviceClass != null) {
                return InjectionPoint.forConstructorOf(serviceClass)
            } else {
                println("!! GuiceEx.transformConstructor.INTERFACE !! rawType=$rawType -> strImpl=$strImpl, serviceClass=$serviceClass")
            }
        }
        return constructorInjector
    }
}

private var guicePatched: Boolean = false

fun createGuiceInjectorEx(modules: List<Module>): Injector {
    if (!guicePatched) {
        guicePatched = true
        val pool: ClassPool = ClassPool.getDefault()
        val cc = pool.get("com.google.inject.internal.ConstructorBindingImpl")
        cc.defrost()
        val m = cc.getDeclaredMethod("create")
        m.insertBefore("{ constructorInjector = ${GuiceEx::class.java.name}.${GuiceEx::transformConstructor.name}(key, constructorInjector); }")
        //cc.writeFile(".")
        cc.toClass()
    }

    return Guice.createInjector(modules.distinct())
}
fun createGuiceInjectorEx(module: Module) = createGuiceInjectorEx(listOf(module))

val Binder.registeredModules by WeakProperty { LinkedHashSet<Module>() }

abstract class GuiceModule(vararg val dependencies: Module) : Module {
    abstract fun Binder.registerBindings()
    final override fun configure(binder: Binder) {
        // Only register this module if not registered previously
        if (!binder.registeredModules.contains(this)) {
            binder.registeredModules += this
            // Register dependencies
            for (dependency in dependencies) dependency.configure(binder)
            binder.registerBindings()
        }
    }
}

fun GuiceModule(vararg dependencies: Module, callback: Binder.() -> Unit) = object : GuiceModule(*dependencies) {
    override fun Binder.registerBindings() = callback()
}

inline fun Binder.bind(module: Module): Binder = this.apply { module.configure(this) }

inline fun <reified T> Binder.bind(instance: T): Binder = this.apply {
    bind(T::class.java).toInstance(instance)
}

inline fun <reified T, reified R : T> Binder.bind(): Binder = this.apply {
    bind(T::class.java).to(R::class.java)
}

inline fun <reified T> Binder.bindSelf(): Binder = this.apply {
    bind(T::class.java).asEagerSingleton()
}

class InjectorNotInContextException : RuntimeException("Injector not in context")

class InjectorContext(val injector: Injector) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<InjectorContext>
}

@KuickInternal
suspend fun injector(): Injector = coroutineContext[InjectorContext]?.injector ?: throw InjectorNotInContextException()

inline fun <reified T : Any> Injector.get() = this.getInstance(T::class.java)
inline fun <reified T : Any> Injector.get(callback: T.() -> Unit) = this.getInstance(T::class.java).apply(callback)
inline fun <reified T : Any> Injector.getOrNull() = try {
    get<T>()
} catch (e: Throwable) {
    null
}
suspend fun <T> withInjectorContextNoIntercepted(injector: Injector, callback: suspend CoroutineScope.() -> T): T =
    withContext(InjectorContext(injector)) { callback() }
suspend fun <T> withInjectorContextIntercepted(injector: Injector, callback: suspend CoroutineScope.() -> T) = injector.runWithInjector { callback(CoroutineScope(coroutineContext)) }

@Deprecated("", ReplaceWith("withInjectorContextIntercepted(injector, callback)"))
suspend fun <T> withInjectorContext(injector: Injector, callback: suspend CoroutineScope.() -> T) = withInjectorContextIntercepted(injector, callback)
