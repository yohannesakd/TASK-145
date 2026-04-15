package com.roadrunner.dispatch.core.domain.model;

public class MatchingWeights {
    public final double timeWindowWeight;
    public final double workloadWeight;
    public final double reputationWeight;
    public final double zoneWeight;

    public MatchingWeights(double timeWindowWeight, double workloadWeight,
                           double reputationWeight, double zoneWeight) {
        this.timeWindowWeight = timeWindowWeight;
        this.workloadWeight = workloadWeight;
        this.reputationWeight = reputationWeight;
        this.zoneWeight = zoneWeight;
    }
}
