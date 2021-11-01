package com.xclydes.finance.longboard.upwork.models;

import java.io.Serializable;
import java.util.List;

public class DiaryRecord implements Serializable {
    public String contract_id;
    public String user_id;
    public String duration;
    public Integer duration_int;
    public Time time;
    public Task task;
    public List<Screenshot> screenshots;

    public static class Time {
        public Integer last_worked_int;
        public Integer tracked_time;
        public Integer manual_time;
        public Integer overtime;
        public String first_worked;
        public String last_worked;
        public Integer first_worked_int;
        public Integer last_screenshot;
    }

    public static class Task {
        public String code;
        public String description;
        public String memo;
        public String id;
    }

    public static class Screenshot {
        public Integer activity;
        public String has_screenshot;
        public String screenshot_url;
        public String screenshot_img;
        public String screenshot_img_med;
        public String screenshot_img_thmb;
        public String screenshot_img_lrg;
        public String has_webcam;
        public String webcam_url;
        public String webcam_img;
        public String webcam_img_thmb;
    }
}
