package kuick.di

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.jar.JarFile

object ClassEnumerator {
    private const val CLASS_SUFFIX = ".class"
    private fun loadClass(cls: String, l: ClassLoader): Class<*> {
        return try {
            //Class.forName(cls)
            l.loadClass(cls)
        } catch (e: ClassNotFoundException) {
            throw ClassEnumException(String.format("Unexpected ClassNotFoundException loading class [%s]", cls), e)
        }
    }

    fun processDirectory(dir: File, pkgname: String, l: ClassLoader): List<Class<*>> {
        val classes: MutableList<Class<*>> = ArrayList()
        for (file in dir.list() ?: arrayOf()) {
            var cls: String? = null
            // we are only interested in .class files
            if (file.endsWith(CLASS_SUFFIX)) {
                // removes the .class extension
                cls = pkgname + '.' + file.substring(0, file.length - 6)
                classes.add(loadClass(cls, l))
            }
            // If the file is a directory recursively class this method.
            val subdir = File(dir, file)
            if (subdir.isDirectory) {
                classes.addAll(processDirectory(subdir, "$pkgname.$file", l))
            }
        }
        return classes
    }

    fun processJarfile(resource: URL, pkgname: String, l: ClassLoader): List<Class<*>> {
        val classes: MutableList<Class<*>> = ArrayList()
        // Turn package name to relative path to jar file
        val relPath = pkgname.replace('.', '/')
        val resPath: String = resource.path
        val jarPath = resPath.replaceFirst("[.]jar[!].*".toRegex(), ".jar").removePrefix("file:")
        try {
            JarFile(jarPath).use { jarFile ->
                // attempt to load jar file

                // get contents of jar file and iterate through them
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Get content name from jar file
                    val entryName = entry.name
                    var className: String? = null

                    // If content is a class save class name.
                    if (entryName.endsWith(CLASS_SUFFIX) && entryName.startsWith(relPath)
                        && entryName.length > relPath.length + "/".length
                    ) {
                        className =
                            entryName.replace('/', '.').replace('\\', '.').removeSuffix(CLASS_SUFFIX)
                    }

                    // If content is a class add class to List
                    if (className != null) {
                        classes.add(loadClass(className, l))
                    }
                }
            }
        } catch (e: IOException) {
            //throw new ClassEnumException(String.format("Unexpected IOException reading JAR File [%s]", jarPath), e);
        }
        return classes
    }

    fun getPackageClasses(pkg: Package, l: ClassLoader = ClassLoader.getSystemClassLoader()): List<Class<*>> =
        getPackageClasses(pkg.name, l)

    fun getPackageClasses(pkgname: String, l: ClassLoader = ClassLoader.getSystemClassLoader()): List<Class<*>> {
        val classes: MutableList<Class<*>> = ArrayList()

        // Get name of package and turn it to a relative path
        //String pkgname = p.getName();
        val relPath = pkgname.replace('.', '/')

        // Get a File object for the package
        return try {
            val resources: Enumeration<URL> = l.getResources(relPath)
            if (!resources.hasMoreElements()) {
                //throw new ClassEnumException(String.format("Unexpected problem: No resource for {%s}", relPath));
            } else {
                do {
                    val resource: URL = resources.nextElement()
                    // If the resource is a jar get all classes from jar
                    if (resource.toString().startsWith("jar:")) {
                        classes.addAll(processJarfile(resource, pkgname, l))
                    } else {
                        val dir = File(resource.path)
                        classes.addAll(processDirectory(dir, pkgname, l))
                    }
                } while (resources.hasMoreElements())
            }
            classes
        } catch (e: IOException) {
            throw ClassEnumException("Unexpected error loading resources", e)
        }
    }

    class ClassEnumException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    }
}
