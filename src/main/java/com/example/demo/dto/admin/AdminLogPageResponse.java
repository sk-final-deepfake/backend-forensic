package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminLogPageResponse {

    private List<AdminLogItemResponse> items;
    private long total;
    private int page;
    private int size;
    private List<String> departments;
}
