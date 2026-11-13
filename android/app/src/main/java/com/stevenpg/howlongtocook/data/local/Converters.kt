package com.stevenpg.howlongtocook.data.local

import androidx.room.TypeConverter
import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.util.AppJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {
    private val stringList: KSerializer<List<String>> = ListSerializer(String.serializer())
    private val methodList: KSerializer<List<CookingMethod>> = ListSerializer(CookingMethod.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String = AppJson.encodeToString(stringList, value)

    @TypeConverter
    fun toStringList(value: String): List<String> = AppJson.decodeFromString(stringList, value)

    @TypeConverter
    fun fromMethods(value: List<CookingMethod>): String = AppJson.encodeToString(methodList, value)

    @TypeConverter
    fun toMethods(value: String): List<CookingMethod> = AppJson.decodeFromString(methodList, value)
}
