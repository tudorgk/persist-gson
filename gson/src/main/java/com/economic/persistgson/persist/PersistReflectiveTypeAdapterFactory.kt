package com.economic.persistgson.persist

import com.economic.persistgson.*
import com.economic.persistgson.internal.ConstructorConstructor
import com.economic.persistgson.internal.Excluder
import com.economic.persistgson.internal.ObjectConstructor
import com.economic.persistgson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory
import com.economic.persistgson.internal.bind.ReflectiveTypeAdapterFactory
import com.economic.persistgson.reflect.TypeToken
import com.economic.persistgson.stream.JsonReader
import com.economic.persistgson.stream.JsonToken
import com.economic.persistgson.stream.JsonWriter

import org.json.JSONObject

import java.io.IOException
import java.util.HashMap

/**
 * Created by Tudor Dragan on 03/05/2017.
 * Copyright © e-conomic.com
 */

class PersistReflectiveTypeAdapterFactory(constructorConstructor: ConstructorConstructor, fieldNamingPolicy: FieldNamingStrategy, excluder: Excluder, jsonAdapterFactory: JsonAdapterAnnotationTypeAdapterFactory) : ReflectiveTypeAdapterFactory(constructorConstructor, fieldNamingPolicy, excluder, jsonAdapterFactory) {

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType

        if (!Any::class.java.isAssignableFrom(raw)) {
            return null // it's a primitive!
        }

        val constructor = constructorConstructor.get(type)
        return Adapter(constructor, getBoundFields(gson, type, raw))
    }

    class Adapter<T> internal constructor(private val constructor: ObjectConstructor<T>, private val boundFields: Map<String, ReflectiveTypeAdapterFactory.BoundField>) : TypeAdapter<T>() {

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): T? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }

            val instance = constructor.construct()

            try {
                `in`.beginObject()
                while (`in`.hasNext()) {
                    val name = `in`.nextName()
                    val field = boundFields[name]
                    if (field == null || !field.deserialized) {
                        // if instance is of type PersisObject
                        if (instance is PersistObject) {
                            val parsedJson = JsonParser().parse(`in`)
                            instance.persistMap.put(name,parsedJson)
                        } else {
                            `in`.skipValue()
                        }
                    } else {
                        field.read(`in`, instance)
                    }
                }
            } catch (e: IllegalStateException) {
                throw JsonSyntaxException(e)
            } catch (e: IllegalAccessException) {
                throw AssertionError(e)
            }

            `in`.endObject()
            return instance
        }

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: T?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginObject()
            try {
                for (boundField in boundFields.values) {
                    if (boundField.writeField(value)) {
                        out.name(boundField.name)
                        boundField.write(out, value)
                    }
                }
            } catch (e: IllegalAccessException) {
                throw AssertionError(e)
            }

            out.endObject()
        }
    }
}