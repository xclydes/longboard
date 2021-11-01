package com.xclydes.finance.longboard.upwork.models;

public class FinanceRecord {
    /**
     * The reference of the accounting transaction.
     */
    public String reference;
    /**
     * The date when the transaction was created.
     */
    public String date;
    /**
     * The date when the the accounting transaction was committed.
     */
    public String date_due;
    /**
     * 	The reference ID of an assignment for which this payment was made, if available.
     */
    public String assignment__reference;
    /**
     * 	The name of the assignment.
     */
    public String assignment_name;
    /**
     * 	The reference ID of an AccountingEntity used for the accounting transaction.
     */
    public String accounting_entity__reference;
    /**
     * The name of the AccountingEntity.
     */
    public String accounting_entity_name;
    /**
     * The purchase order number.
     */
    public String buyer_company__reference;
    /**
     * The literal ID of the client’s company.
     */
    public String buyer_company__id;
    /**
     * TThe name of the client’s company.
     */
    public String buyer_company_name;
    /**
     * The reference ID of the client’s team that posts owns the assignment.
     */
    public String buyer_team__reference;
    /**
     * The literal ID of the client’s team.
     */
    public String buyer_team__id;
    /**
     * 	The name of the client’s team.
     */
    public String buyer_team_name;
    /**
     * The reference ID of the freelancer’s company.
     */
    public String provider_company__reference;
    /**
     * 	The literal ID of the freelancer’s company.
     */
    public String provider_company__id;
    /**
     * The name of the freelancer’s company.
     */
    public String provider_company_name;
    /**
     * 	The reference ID of the freelancer’s team that the freelancer is staffed under for the assignment.
     */
    public String provider_team__reference;
    /**
     * The literal ID of the freelancer’s team.
     */
    public String provider_team__id;
    /**
     * The name of the freelancer’s team.
     */
    public String provider_team_name;
    /**
     * The reference ID of the freelancer who works under the assignment.
     */
    public String provider__reference;
    /**
     * 	The literal ID of the freelancer.
     */
    public String provider__id;
    /**
     * 	The name of the freelancer.
     */
    public String provider_name;
    /**
     * 	The transaction type - Invoice, Payment, Adjustment.
     */
    public String type;
    /**
     * The detailed transaction type -
     * Hourly, Fixed Price, Bonus, Refund, Withdrawal, Payment, Adjustment, Salary,
     * Security Deposit, Chargeback, Chargeback Resolution, Referral,
     * Customer Satisfaction, Expense, Overtime, Milestone,
     * Upfront Payment, Credit.
     */
    public String subtype;
    /**
     * 	The description of the transaction.
     */
    public String description;
    /**
     * The comment given for adjustments.
     */
    public String comment;
    /**
     * The memo for the transaction.
     */
    public String memo;
    /**
     * The added by user notes.
     */
    public String notes;
    /**
     * The amount of the transaction.
     * Supports aggregation
     */
    public String amount;
    /**
     * The purchase order number.
     */
    public String po_number;
}
