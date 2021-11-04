package com.xclydes.finance.longboard.upwork.models;

import java.io.Serializable;

public class User implements Serializable {
    public String id;
    public String timezone;
    public String status;
    public String timezone_offset;
    public String public_url;
    public String last_name;
    public String email;
    public String reference;
    public String is_provider;
    public String first_name;
    public String profile_key;
    public Profile profile;
    public String has_contract;

    public static class Profile {
        public String ciphertext;
        public String dev_ac_agencies;
        public String dev_adj_score;
        public String dev_adj_score_recent;
        public String dev_billed_assignments;
        public String dev_city;
        public String dev_country;
        public String dev_eng_skill;
        public String dev_groups;
        public String dev_is_affiliated;
        public String dev_last_activity;
        public String dev_last_worked;
        public String dev_last_worked_ts;
        public String dev_portfolio_items_count;
        public String dev_portrait;
        public String dev_portrait_100;
        public String dev_portrait_32;
        public String dev_portrait_50;
        public String dev_profile_title;
        public String dev_recno_ciphertext;
        public String dev_short_name;
        public String dev_timezone;
        public String dev_tot_feedback;
        public String dev_total_hours;
        public String dev_ui_profile_access;
    }
}


