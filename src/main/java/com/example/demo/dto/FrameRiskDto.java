package com.example.demo.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrameRiskDto {

    private int frameIndex;
    private double timestampSec;
    private double riskScore;
    /** TruFor localization connected-component boxes (video pixel space). */
    private List<TamperBBoxDto> bboxes;
}
