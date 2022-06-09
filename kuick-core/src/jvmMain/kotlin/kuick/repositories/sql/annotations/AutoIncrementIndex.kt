package kuick.repositories.sql.annotations

/**
Define que el campo se autoincrementa en la BBDD (tipo de dato serial en postgresql)
 No añade el campo en los inserts
 **/

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class AutoIncrementIndex
