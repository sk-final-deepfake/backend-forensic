package com.example.demo.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveAdminUserRequest {

    /** INVESTIGATOR · REVIEWER · ORG_ADMIN */
    private String role;
}
