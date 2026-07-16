package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TamperBBoxDto {

    private int x;
    private int y;
    private int w;
    private int h;
    private Double score;
}
