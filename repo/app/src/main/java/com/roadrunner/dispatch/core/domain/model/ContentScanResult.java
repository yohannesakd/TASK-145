package com.roadrunner.dispatch.core.domain.model;

import java.util.List;

public class ContentScanResult {
    public final String status;
    public final List<String> matchedTerms;
    public final boolean isZeroTolerance;

    public ContentScanResult(String status, List<String> matchedTerms, boolean isZeroTolerance) {
        this.status = status;
        this.matchedTerms = matchedTerms;
        this.isZeroTolerance = isZeroTolerance;
    }
}
