package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.MatchingWeights;
import com.roadrunner.dispatch.core.domain.model.ScoredWorker;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchTasksUseCase {
    private static final int DEFAULT_MAX_WORKLOAD = 10;
    private static final long HOUR_MS = 3_600_000L;

    private final WorkerRepository workerRepository;
    private final ZoneRepository zoneRepository;
    private final TaskRepository taskRepository;

    public MatchTasksUseCase(WorkerRepository workerRepository,
                              ZoneRepository zoneRepository,
                              TaskRepository taskRepository) {
        this.workerRepository = workerRepository;
        this.zoneRepository = zoneRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * For ASSIGNED mode: rank available workers for a given task.
     * Higher score = better match.
     */
    public List<ScoredWorker> rankWorkersForTask(Task task, MatchingWeights weights, String orgId) {
        List<Worker> availableWorkers = workerRepository.getWorkersByStatus(orgId, "AVAILABLE");
        Zone taskZone = task.zoneId != null ? zoneRepository.getByIdScoped(task.zoneId, orgId) : null;
        int taskZoneScore = taskZone != null ? taskZone.score : 3;

        long now = System.currentTimeMillis();
        List<ScoredWorker> scored = new ArrayList<>();

        for (Worker worker : availableWorkers) {
            Map<String, Double> breakdown = new HashMap<>();

            // Time window adherence
            double timeScore = computeTimeWindowScore(task.windowStart, task.windowEnd, now);
            breakdown.put("timeWindow", timeScore);

            // Workload score (inverse: fewer tasks = better)
            double workloadScore = 1.0 - ((double) worker.currentWorkload / DEFAULT_MAX_WORKLOAD);
            workloadScore = Math.max(0.0, Math.min(1.0, workloadScore));
            breakdown.put("workload", workloadScore);

            // Reputation score (normalized 0-1 from 0-5 scale)
            double repScore = worker.reputationScore / 5.0;
            repScore = Math.max(0.0, Math.min(1.0, repScore));
            breakdown.put("reputation", repScore);

            // Zone distance penalty (0-1 where 0 = same zone, 1 = max distance)
            Zone workerZone = worker.zoneId != null ? zoneRepository.getByIdScoped(worker.zoneId, orgId) : null;
            int workerZoneScore = workerZone != null ? workerZone.score : 3;
            double zoneDistance = Math.abs(workerZoneScore - taskZoneScore) / 4.0;
            breakdown.put("zoneDistance", zoneDistance);

            // Weighted total score
            double totalScore = (weights.timeWindowWeight * timeScore)
                    + (weights.workloadWeight * workloadScore)
                    + (weights.reputationWeight * repScore)
                    - (weights.zoneWeight * zoneDistance);

            if (totalScore > 0) {
                scored.add(new ScoredWorker(worker, totalScore, breakdown));
            }
        }

        // Sort descending by score
        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));
        return scored;
    }

    /**
     * For GRAB_ORDER mode: rank open grab-order tasks for a given worker.
     * Higher score = more attractive task for the worker.
     */
    public List<ScoredTask> rankTasksForWorker(Worker worker, MatchingWeights weights, String orgId) {
        long now = System.currentTimeMillis();
        List<Task> openTasks = taskRepository.getOpenTasks(orgId, "GRAB_ORDER", now);

        Zone workerZone = worker.zoneId != null ? zoneRepository.getByIdScoped(worker.zoneId, orgId) : null;
        int workerZoneScore = workerZone != null ? workerZone.score : 3;

        List<ScoredTask> scored = new ArrayList<>();

        for (Task task : openTasks) {
            Map<String, Double> breakdown = new HashMap<>();

            // Time window urgency: tasks with tighter windows score higher
            double timeScore = computeTimeWindowScore(task.windowStart, task.windowEnd, now);
            breakdown.put("timeWindow", timeScore);

            // Priority score (normalized; higher int priority = higher score)
            int priorityInt;
            try {
                priorityInt = Integer.parseInt(task.priority);
            } catch (NumberFormatException e) {
                priorityInt = 0;
            }
            double priorityScore = Math.max(0.0, Math.min(1.0, priorityInt / 10.0));
            breakdown.put("priority", priorityScore);

            // Zone proximity (penalty decreases score when task is far from worker zone)
            Zone taskZone = task.zoneId != null ? zoneRepository.getByIdScoped(task.zoneId, orgId) : null;
            int taskZoneScore = taskZone != null ? taskZone.score : 3;
            double zoneDistance = Math.abs(taskZoneScore - workerZoneScore) / 4.0;
            breakdown.put("zoneDistance", zoneDistance);

            // Worker workload inverse: busy workers get lower task attractiveness
            double workloadScore = 1.0 - ((double) worker.currentWorkload / DEFAULT_MAX_WORKLOAD);
            workloadScore = Math.max(0.0, Math.min(1.0, workloadScore));
            breakdown.put("workload", workloadScore);

            // Worker reputation (normalized 0-1 from 0-5 scale)
            double repScore = worker.reputationScore / 5.0;
            repScore = Math.max(0.0, Math.min(1.0, repScore));
            breakdown.put("reputation", repScore);

            // Weighted total score for task attractiveness:
            // reputationWeight applies to worker reputation; priority is folded into timeWindow scoring
            double totalScore = (weights.timeWindowWeight * (timeScore + priorityScore) / 2.0)
                    + (weights.workloadWeight * workloadScore)
                    + (weights.reputationWeight * repScore)
                    - (weights.zoneWeight * zoneDistance);

            if (totalScore > 0) {
                scored.add(new ScoredTask(task, totalScore, breakdown));
            }
        }

        // Sort descending by score
        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));
        return scored;
    }

    private double computeTimeWindowScore(long windowStart, long windowEnd, long now) {
        if (now >= windowStart && now <= windowEnd) {
            return 1.0; // Currently within window
        }
        if (now < windowStart) {
            long gap = windowStart - now;
            if (gap <= HOUR_MS) return 0.8;
            if (gap <= 4 * HOUR_MS) return 0.5;
            return 0.2;
        }
        // Past window end
        return 0.0;
    }

    /** Ranked task for grab-order mode. */
    public static class ScoredTask {
        public final Task task;
        public final double score;
        public final Map<String, Double> breakdown;

        public ScoredTask(Task task, double score, Map<String, Double> breakdown) {
            this.task = task;
            this.score = score;
            this.breakdown = breakdown;
        }
    }
}
