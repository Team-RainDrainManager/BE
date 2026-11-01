package com.rainbutler.domain.inspection.dto;

/** 점검 가능 여부입니다. */
public record InspectionEligibilityResponse(
        boolean eligible,
        InspectionEligibilityReason reason,
        String message
) {

    public static InspectionEligibilityResponse allowed() {
        return new InspectionEligibilityResponse(true, null, null);
    }

    public static InspectionEligibilityResponse ineligible(
            InspectionEligibilityReason reason, String message) {
        return new InspectionEligibilityResponse(false, reason, message);
    }
}
