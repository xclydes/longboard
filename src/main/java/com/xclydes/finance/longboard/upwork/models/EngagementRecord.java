package com.xclydes.finance.longboard.upwork.models;

public class EngagementRecord {
    public String job_ref_ciphertext;
    public String status;
    public String engagement_job_type;
    public String offer_id;
    public String job__title;
    public String cj_job_application_uid;
    public String provider_team__id;
    public String dev_recno_ciphertext;
    public String active_milestone;
    public String provider__id;
    public String engagement_title;
    public String buyer_team__reference;
    public String buyer_team__id;
    public String portrait_url;
    public String category_uid;
    public String category_name;

    // These look like decimals
    public Float hourly_charge_rate;
    public Float weekly_salary_charge_amount;
    public Float fixed_charge_amount_agreed;
    public Float fixed_price_upfront_payment;

    // These look like ints
    public String reference;
    public String job_application_ref;
    public String parent_reference;
    public String provider__reference;
    public String provider_team__reference;
    public Integer weekly_stipend_hours;
    public Integer weekly_hours_limit;

    // These might be dates
    public String engagement_end_ts;
    public String engagement_start_date;
    public String created_time;
    public String engagement_end_date;
    public String engagement_start_ts;

    // These look like booleans
    // Comes back as 0 or 1
    public String is_suspended;
    public String is_autopaused;
    public String is_paused;

    public Feedback feedback;

    public static class FeedbackForProvider{
        public String score;
    }

    public static class FeedbackForBuyer{
        public String score;
    }

    public static class Feedback{
        public FeedbackForProvider feedback_for_provider;
        public FeedbackForBuyer feedback_for_buyer;
    }
}
