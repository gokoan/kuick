package kuick.repositories.sql

interface SerializationStrategy {

    /**
     * Convierte un valor [dbValue] con el tipo almacenado en la BBDD al tipo destino del objeto a mapear
     */
    fun fromDatabaseValue(parameterData: ParameterReflectInfo, dbValue: Any?): Any?

    /**
     * Convierte un valor [objValue] del objeto al tipo que queremos almacenar en la BBDD
     */
    fun toDatabaseValue(objValue: Any?, annotations: List<Annotation> = listOf()): Any?



    /**
     * Builds a model from a list of values
     */
    fun <T : Any> modelFromValues(reflectionInfo: ModelReflectionInfo<T>, fieldValues: List<Any?>): T {
        val constructor = reflectionInfo.constructor
        val mappedArgs = reflectionInfo.constructorArgs
            .zip(fieldValues)
            .map { (param, value) ->
                try {
                    fromDatabaseValue(param, value)
                } catch (t: Throwable) {
                    System.err.println("JASYNC MAPPING ERROR -------------------------")
                    System.err.println("Problem mapping param: ${param.name}")
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
