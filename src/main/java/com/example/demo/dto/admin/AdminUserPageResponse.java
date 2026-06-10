package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminUserPageResponse {

    private List<AdminUserItemResponse> items;
    private long total;
    private int page;
    private int size;
}
