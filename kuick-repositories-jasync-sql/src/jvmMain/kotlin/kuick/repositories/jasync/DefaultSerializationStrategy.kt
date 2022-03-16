package kuick.repositories.jasync

import com.google.gson.*
import kuick.json.DateAdapter
import kuick.json.LocalDateTimeAdapter
import kuick.json.LocalTimeAdapter
import kuick.models.Email
import kuick.models.Id
import kuick.models.KLocalDate
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

open class DefaultSerializationStrategy: SerializationStrategy {

    private val LOCAL_DATE_FMT = DateTimeFormatter.ISO_DATE
    private val LOCAL_DATE_TIME_FMT = DateTimeFormatter.ISO_DATE_TIME


    override fun fromDatabaseValue(kType: KType, dbValue: Any?): Any? {
        val targetClass: KClass<*> = kType.classifier as KClass<*>
        val out = when {
            dbValue == null -> null
            targetClass == String::class -> dbValue
            targetClass == Long::class -> dbValue
            targetClass == Boolean::class -> dbValue
            targetClass == Int::class && dbValue is Number -> dbValue.toInt()
            targetClass == Double::class && dbValue is Number -> dbValue.toDouble()
            targetClass == Float::class -> dbValue

            // Los IDs
            targetClass.isSubclassOf(Id::class) ->
                targetClass.primaryConstructor?.call(dbValue.toString()) as? Id?

            // Los Emails
            targetClass == Email::class -> Email(dbValue.toString())

            // En GoKoan las LocalDate se almacenan como String
            targetClass == LocalDate::class && dbValue is String-> LocalDate.parse(dbValue, LOCAL_DATE_FMT)
            targetClass == LocalDate::class && dbValue is org.joda.time.LocalDate -> LocalDate.of(dbValue.year, dbValue.monthOfYear, dbValue.dayOfMonth)
            targetClass == LocalDateTime::class && dbValue is String-> LocalDateTime.parse(dbValue, LOCAL_DATE_TIME_FMT)
            targetClass == LocalDateTime::class && dbValue is org.joda.time.LocalDateTime-> LocalDateTime.of(dbValue.year, dbValue.monthOfYear, dbValue.dayOfMonth, dbValue.hourOfDay, dbValue.minuteOfHour, dbValue.secondOfMinute)

            targetClass == org.joda.time.LocalDate::class && dbValue is org.joda.time.LocalDate -> dbValue
            targetClass == org.joda.time.LocalDateTime::class && dbValue is org.joda.time.LocalDateTime -> dbValue

            // En GoKoan las KLocalDate se almacenan como String
            targetClass == KLocalDate::class && dbValue is String-> KLocalDate(dbValue)

            targetClass.java.isEnum -> {
                val normalizedValue = dbValue.toString().trim().replace("\"", "")
                targetClass.java.enumConstants.firstOrNull { (it as Enum<*>).name == normalizedValue }
            }

            // Las clases complejas se almaenan como JSON
            else -> dbJson.fromJson(dbValue.toString(), kType.javaType)
        }
        return out
    }


    override fun toDatabaseValue(objValue: Any?): Any? = when {
        // Los tipos básicos se mapean al mismo tipo en BBDD
        objValue == null || objValue is String || objValue is Boolean || objValue is Int ||
            objValue is Long || objValue is Float || objValue is Double
        -> objValue

        // Los Joda time también se mapean tal cuál a jasync
        objValue is org.joda.time.LocalDateTime -> objValue
        objValue is org.joda.time.LocalDate -> objValue


        // Los IDs de kuick se mapean a String
        objValue is Id -> objValue.id

        // Los emails de kuick se mapean a String
        objValue is Email -> objValue.email

        // Las LocalDate como String
        objValue is LocalDate -> objValue.format(LOCAL_DATE_FMT)
        objValue is LocalDateTime -> objValue.format(LOCAL_DATE_TIME_FMT)

        objValue is KLocalDate -> objValue.toString()

        objValue.javaClass.isEnum -> (objValue as Enum<*>).name

        // El resto lo mapeamos a JSON
        else -> dbJson.toJson(objValue)
    }

    private val dbJson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Id::class.java, IdGsonAdapter())
        .registerTypeAdapter(Date::class.java, DateAdapter())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .create()

    class IdGsonAdapter : JsonDeserializer<Id>, JsonSerializer<Id> {

        override fun deserialize(je: JsonElement, type: Type, ctx: JsonDeserializationContext): Id {
            val constuctor = (type as Class<*>).declaredConstructors.first { it.parameterCount == 1 }
                ?: error("Can't find a constructor with one argument for $type")
            val idString = when {
                je is JsonObject && je.has(Id::id.name) -> je.get(Id::id.name).asString
                je is JsonObject -> {
                    var idValue: String? = null
                    for (key in je.keySet()) {
                        // This handle mangling issues. Eg. "id_ursnrc$_0"
                        if (key.startsWith("id")) {
                            idValue = je.get(key).asString
                            break
                        }
                    }
                    idValue
                }
                else -> je.asString
            }
            return constuctor.newInstance(idString) as Id
        }

        override fun serialize(id: Id?, type: Type, ctx: JsonSerializationContext): JsonElement {
            return JsonPrimitive(id?.id)
        }
    }


}
