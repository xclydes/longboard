package com.xclydes.finance.longboard.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ArrayUtil {

    public static <T> void enforceZippable(final T[] parts) {
        // Ensure its an even number
        if(parts.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of parts: " + Arrays.toString(parts));
        }
    }

    public static <T> Map<T, T> zipWithNext(final T[] parts) {
        // Ensure its an even number
        enforceZippable(parts);
        // Create the new map
        final Map<T, T> mapped = new HashMap<>();
        for(int indx = 0; indx < parts.length; indx+=2){
            mapped.put(parts[indx], parts[indx+1]);
        }
        return mapped;

    }
 }
