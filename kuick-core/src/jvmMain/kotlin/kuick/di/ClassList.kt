package kuick.di

class ClassList(private val classes: List<Class<*>>, val loader: ClassLoader) {
    companion object {
        fun forPackage(pkg: String, l: ClassLoader = ClassLoader.getSystemClassLoader()) =
            ClassList(ClassEnumerator.getPackageClasses(pkg, l), l)
    }

    fun all(): List<Class<*>> = classes.toList()

    fun annotatedBy(annotationClass: Class<out Annotation>): List<Class<*>> =
        classes.filter { it.getAnnotation(annotationClass) != null }.toList()

    fun isAssignableBy(baseClass: Class<*>): List<Class<*>> =
        classes.filter { baseClass.isAssignableFrom(it) }.toList()

    // Compatibility with reflections8
    fun getTypesAnnotatedWith(annotationClass: Class<out Annotation>, dummy: Boolean) = annotatedBy(annotationClass)
    fun getSubTypesOf(baseClass: Class<*>) = isAssignableBy(baseClass)
}

fun main() {

    ClassList.forPackage("kuick").all()
        .filter { it.simpleName.isNotEmpty() }
        .sortedBy { it.name }
        .forEach {
        println(it.name)
    }
}
