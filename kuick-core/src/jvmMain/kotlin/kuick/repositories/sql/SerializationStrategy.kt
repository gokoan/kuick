package kuick.repositories.sql

import kotlin.reflect.KClass
import kotlin.reflect.KType

interface SerializationStrategy {

    /**
     * Convierte un valor [dbValue] con el tipo almacenado en la BBDD al tipo destino del objeto a mapear
     */
    fun fromDatabaseValue(kType: KType, dbValue: Any?): Any?

    /**
     * Convierte un valor [objValue] del objeto al tipo que queremos almacenar en la BBDD
     */
    fun toDatabaseValue(objValue: Any?): Any?



    /**
     * Builds a model from a list of values
     */
    fun <T : Any> modelFromValues(modelClass: KClass<T>, fieldValues: List<Any?>): T {
        val constructor = modelClass.constructors.first()
        val constructorArgs = constructor.parameters
        val mappedArgs = constructorArgs
            .zip(fieldValues)
            .map { (param, value) ->
                try {
                    fromDatabaseValue(param.type, value)
                } catch (t: Throwable) {
                    System.err.println("JASYNC MAPPING ERROR -------------------------")
                    System.err.println("Problem mapping param: $param")
                    System.err.println("Database value: $value [${value?.let { it::class }}]")
                    System.err.println("Constructor: $constructor")
                    fieldValues.forEach {
                        System.err.println(" - ${it?.javaClass?.simpleName} : ${it}")
                    }
                    System.err.println("/JASYNC MAPPING ERROR -------------------------")
                    throw t
                }
            }
        try {
            return constructor.call(*mappedArgs.toTypedArray())
        } catch (iae: Throwable) {
            System.err.println("JASYNC CONSTRUCTOR BUILDING ERROR -------------------------")
            System.err.println("Constructor: $constructor")
            fieldValues.forEach {
                System.err.println(" - ${it?.javaClass?.simpleName} : ${it}")
            }
            System.err.println("/JASYNC CONSTRUCTOR BUILDING ERROR -------------------------")
            throw iae
        }
    }

}
