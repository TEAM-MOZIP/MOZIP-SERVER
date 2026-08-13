package com.mozip.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MappingAxis {

    @JsonProperty("gender")
    GENDER,

    @JsonProperty("age_group")
    AGE_GROUP,

    @JsonProperty("region")
    REGION,

    @JsonProperty("employment_status")
    EMPLOYMENT_STATUS,

    @JsonProperty("household_type")
    HOUSEHOLD_TYPE,

    @JsonProperty("income_type")
    INCOME_TYPE
}
