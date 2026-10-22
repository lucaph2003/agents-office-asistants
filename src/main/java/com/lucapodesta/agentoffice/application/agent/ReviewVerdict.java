package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.domain.review.ReviewStatus;

public record ReviewVerdict(ReviewStatus status, String feedback) {
}
