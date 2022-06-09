package kuick.repositories.sql.annotations

/**
Define que la lista se almacena como un Array en la BBDD y no como un string
Afecta a la estrategia de serialización
 **/

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class AsArray
