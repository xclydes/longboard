package com.xclydes.finance.longboard.upwork.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team implements Serializable {
    public String id;
    public String name;
    public String reference;
    public String company__reference;
    public String company_name;
    public String parent_team__id;
    public String parent_team__name;
    public String parent_team__reference;
    public String payment_verification_status;
}
