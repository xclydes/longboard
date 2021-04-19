package com.xclydes.finance.longboard.component

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.wave.GetBusinessCustomersQuery
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.stereotype.Component

@Component
class UpworkToWaveConversion : ConversionService {

    private val objectMapper by lazyOf(ObjectMapper())
    private val objectReader by lazy { objectMapper.reader() }

    override fun canConvert(sourceType: Class<*>?, targetType: Class<*>): Boolean =
        sourceType is ObjectNode &&
        (
          GetBusinessCustomersQuery.Node::class.isInstance(targetType)
        )

    override fun canConvert(sourceType: TypeDescriptor?, targetType: TypeDescriptor): Boolean =
        sourceType is ObjectNode &&
        (
            GetBusinessCustomersQuery.Node::class.isInstance(targetType)
        )

    override fun <T : Any?> convert(source: Any?, targetType: Class<T>): T? {
        var instance: Any? = null;
        if (source is JsonNode) {
            when(targetType) {
                GetBusinessCustomersQuery.Node::class.java -> instance = GetBusinessCustomersQuery.Node(
                    displayId =  source.required("reference").asText(),
                    name =  source.required("company_name").asText(),
                    internalNotes = (objectReader.createObjectNode() as ObjectNode).putPOJO("upwork", source).toPrettyString(),
                    firstName =  source.required("name").asText(),
                    lastName = "",
                    id = ""
                )
            }
        }
        return instance as T?
    }

    override fun convert(source: Any?, sourceType: TypeDescriptor?, targetType: TypeDescriptor): Any? {
        TODO("Not yet implemented")
    }
}
