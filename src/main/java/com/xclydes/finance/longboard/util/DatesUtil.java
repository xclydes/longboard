package com.xclydes.finance.longboard.util;

import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

public class DatesUtil
{
    private static final Object SYNC = new Object();

    private static DateTimeFormatter dateFormatHuman;//: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    private static DateTimeFormatter dateFormatSQL;//: DateTimeFormatter by lazyOf(DateTimeFormatter.ISO_DATE)
    private static DateTimeFormatter dateFormatReport;//: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("yyyyMMdd"))
    private static DateTimeFormatter dateFormatDescription;//: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("MM/dd/yyyy"))

    private static DateTimeFormatter getOrInit(final DateTimeFormatter current, final Supplier<DateTimeFormatter> supplier) {
        DateTimeFormatter formatter = current;
        if(formatter == null) {
            synchronized (SYNC) {
                formatter = supplier.get();
            }
        }
        return formatter;
    }

    public static DateTimeFormatter formatterHuman() {
        return getOrInit(dateFormatHuman, () -> {
            dateFormatHuman = DateTimeFormatter.ofPattern("MMM dd, yyyy");
            return dateFormatHuman;
        });
    }

    public static DateTimeFormatter formatterSQL() {
        return getOrInit(dateFormatSQL, () -> {
            dateFormatSQL = DateTimeFormatter.ISO_DATE;
            return dateFormatSQL;
        });
    }

    public static DateTimeFormatter formatterReport() {
        return getOrInit(dateFormatReport, () -> {
            dateFormatReport = DateTimeFormatter.ofPattern("yyyyMMdd");
            return dateFormatReport;
        });
    }

    public static DateTimeFormatter formatterDescriptive() {
        return getOrInit(dateFormatDescription, () -> {
            dateFormatDescription = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            return dateFormatDescription;
        });
    }
}
