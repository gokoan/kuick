package kuick.repositories.sql.annotations

/**
Define que la variable de tipo Id se almacena como un número en la BBDD
Afecta a la estrategia de serialización
 **/

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class AsNumericId
