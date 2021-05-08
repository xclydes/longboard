package com.xclydes.finance.longboard.component

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.wave.GetBusinessCustomersQuery
import com.xclydes.finance.longboard.wave.GetBusinessInvoiceQuery
import com.xclydes.finance.longboard.wave.type.CurrencyCode
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.*

@Component
class UpworkToWaveConversion(private val waveSvc: WaveSvc) : ConversionService {

    private val objectMapper by lazyOf(ObjectMapper())
    private val objectReader by lazy { objectMapper.reader() }

    private val currencyFormatter by lazy {
        NumberFormat.getCurrencyInstance().also {
            it.maximumFractionDigits = 0
            it.currency = Currency.getInstance("USD")
        }
    }

    override fun canConvert(sourceType: Class<*>?, targetType: Class<*>): Boolean =
        sourceType is ObjectNode &&
        (
          GetBusinessCustomersQuery.Node::class.isInstance(targetType)
        )

    override fun canConvert(sourceType: TypeDescriptor?, targetType: TypeDescriptor): Boolean =
        canConvert(sourceType!!.objectType, targetType.objectType)

    override fun <T : Any?> convert(source: Any?, targetType: Class<T>): T? {
        var instance: Any? = null;
        if (source is JsonNode) {
            when(targetType) {
                GetBusinessCustomersQuery.Node::class.java -> instance = asCustomer(source)

            }
        }
        return instance as T?
    }

    override fun convert(source: Any?, sourceType: TypeDescriptor?, targetType: TypeDescriptor): Any? =
        convert(source, targetType.objectType)

    private fun asCustomer(source: JsonNode) : GetBusinessCustomersQuery.Node = GetBusinessCustomersQuery.Node(
        displayId =  source.required("reference").asText(),
        name =  source.required("company_name").asText(),
        internalNotes = (objectReader.createObjectNode() as ObjectNode).putPOJO("upwork", source).toPrettyString(),
        firstName =  source.required("name").asText(),
        lastName = "",
        id = ""
    )
}
