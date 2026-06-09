package com.example.demo.dto.mypage;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseSummaryResponse {

	private String caseId;
	private String caseName;
	private String status;
	private String createdAt;
	private int evidenceCount;
}
