package com.xclydes.finance.longboard.wave.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

@Data
@AllArgsConstructor
public class InvoiceInput {

    public final String reference;
    public final String customerID;
    public final String title;
    public final String memo;
    public final LocalDate datePosted;
    public final LocalDate dateDue;
    public final Collection<TransactionLineItem> lineItems;

    @Data
    @AllArgsConstructor
    public static class TransactionLineItem {
        public final String productID;
        public final String description;
        public final Double quantity;
        public final Double unitPrice;
    }

}
