package com.xclydes.finance.longboard.wave.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class InvoicePayment {

    public enum Method {
        bank_transfer,
        cash,
        cheque,
        credit_card,
        paypal,
        other
    }

    public final String invoiceID;
    public final String accountClassicID;
    public final LocalDate date;
    public final Double amount;
    public final Method method;
    public final String memo;
}
