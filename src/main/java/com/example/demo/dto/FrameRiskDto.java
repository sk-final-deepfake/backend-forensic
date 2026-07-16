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
    /** TruFor localization boxes (video pixel space). Optional for deepfake modules. */
    private List<TamperBBoxDto> bboxes;
}
