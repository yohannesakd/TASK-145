package com.roadrunner.dispatch.core.domain.model;

import java.util.Map;

public class ScoredWorker {
    public final Worker worker;
    public final double score;
    public final Map<String, Double> breakdown;

    public ScoredWorker(Worker worker, double score, Map<String, Double> breakdown) {
        this.worker = worker;
        this.score = score;
        this.breakdown = breakdown;
    }
}
