package com.xclydes.finance.longboard.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.json.JSONArray
import org.json.JSONObject
import java.util.stream.IntStream
import kotlin.streams.toList

class JsonUtil {

    companion object {

        private val objectMapper by lazyOf(ObjectMapper())
        private val objectReader by lazy { objectMapper.reader() }

        fun jsonArrayToList(arr: JSONArray): List<JSONObject> = IntStream
            .range(0, arr.length())
            .mapToObj(arr::getJSONObject)
            .toList()

        fun toJacksonArray(arr: JSONArray): ArrayNode = with(objectReader.createArrayNode() as ArrayNode)
        {
            jsonArrayToList( arr )
                .map{ elem -> objectReader.readTree( elem.toString(0))}
                .forEach(this::add)
            return this
        }

        fun toJacksonObject(obj: JSONObject): ObjectNode = objectReader.readTree( obj.toString(0)) as ObjectNode
    }

}
