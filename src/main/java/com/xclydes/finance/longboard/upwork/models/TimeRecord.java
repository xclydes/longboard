package com.xclydes.finance.longboard.upwork.models;

import java.io.Serializable;

public class TimeRecord implements Serializable {
    /**
     * 	The date when work was performed by the freelancer.
     */
    public String worked_on;
    /**
     * 	Monday’s date corresponding to the week when the worked_on occurs.
     */
    public String week_worked_on;
    /**
     * The month, in number, for the date in the worked_on field.
     */
    public String month_worked_on;
    /**
     * The year in the date that appears in the Start Date field.
     */
    public String year_worked_on;
    /**
     * The ID of freelancer.
     */
    public String provider_id;
    /**
     * 	The freelancer’s name.
     */
    public String provider_name;
    /**
     * 	The ID of team billed.
     */
    public String team_id;
    /**
     * The name of team billed.
     */
    public String team_name;
    /**
     * 	The ID of the hiring team in the assignment.
     */
    public String assignment_team_id;
    /**
     * The opening title of the assignment.
     */
    public String assignment_name;
    /**
     * The contract ID.
     */
    public String assignment_ref;
    /**
     * The team ID of the agency.
     */
    public String agency_id;
    /**
     * 	The name of the agency.
     */
    public String agency_name;
    /**
     * 	The team ID of rollup assignment_team_id.
     */
    public String company_id;
    /**
     * 	The activities which the freelancer worked on.
     */
    public String task;
    /**
     * 	The memos logged by the freelancer during work.
     */
    public String memo;
    /**
     * The agency ID of rollup agency_id.
     */
    public String agency_company_id;
    /**
     * The total hours worked by the freelancer
     * during the date of worked_on.
     * Supports aggregation
     */
    public String hours;
    /**
     * The total amount charged to the client.
     * Supports aggregation
     */
    public String charges;
    /**
     * The number of online hours in hours.
     * Supports aggregation
     */
    public String hours_online;
    /**
     * The charges of work performed online.
     * Supports aggregation
     */
    public String charges_online;
    /**
     * The number of offline hours, in hours.
     * Supports aggregation
     */
    public String hours_offline;
    /**
     * The charges of work performed offline.
     * Supports aggregation
     */
    public String charges_offline;

    public String reference;
    public String buyer_team__reference;
    public String date;
    public String amount;
    public String type;
    public String subtype;
    public String description;
    public String date_due;
}
