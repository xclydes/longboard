package com.xclydes.finance.longboard.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectReader objectReader = objectMapper.reader();

    public static ObjectNode newObject() { return (ObjectNode) objectReader.createObjectNode(); }
    public static ArrayNode newArray() { return (ArrayNode) objectReader.createArrayNode(); }

    public static List<JSONObject> jsonArrayToList(final JSONArray arr) {
        return IntStream
                .range(0, arr.length())
                .mapToObj(indx -> {
                    try {
                       return arr.getJSONObject(indx);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static ArrayNode toJacksonArray(final JSONArray arr) {
        final ArrayNode nArr = newArray();
        jsonArrayToList( arr )
                .stream().map( elem -> {
                    try {
                        return toJacksonObject(elem);
                    } catch (JSONException | JsonProcessingException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(nArr::add);
        return nArr;
    }

    public static ObjectNode toJacksonObject(final JSONObject obj) throws JSONException, JsonProcessingException {
        return (ObjectNode) objectReader.readTree( obj.toString(0));
    }

}
