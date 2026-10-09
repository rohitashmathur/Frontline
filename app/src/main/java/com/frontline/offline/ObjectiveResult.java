package com.frontline.offline;

/** Objective facts and stable message keys; presentation formats the numeric arguments. */
public final class ObjectiveResult {
    public enum Policy { CAMPAIGN, HISTORICAL, ELAPSED_TIME, COMPLETION_ONLY, DEPLOYED_TROOPS, NONE }

    public static final String IN_PROGRESS = "result.in_progress";
    public static final String CAMPAIGN_WON = "result.campaign_won";
    public static final String HOLD_COMPLETED = "result.hold_completed";
    public static final String KEEP_COMPLETED = "result.keep_completed";
    public static final String BUDGET_COMPLETED = "result.budget_completed";
    public static final String BUDGET_EXCEEDED = "result.budget_exceeded";
    public static final String PROTECTED_KING_LOST = "result.protected_king_lost";
    public static final String PLAYER_ELIMINATED = "result.player_eliminated";
    public static final String SURRENDERED = "result.surrendered";
    public static final String LEGACY_DEFEAT = "result.legacy_defeat";
    public static final String HISTORICAL_LABEL = "record.historical";

    public final String code;
    public final Policy policy;
    public final int objectiveType, outcome, terminalReason;
    public final boolean completed, historical;
    public final float elapsedSeconds, actualSeconds, requiredSeconds;
    public final int unitsSent, budget, remainingAllowance, captures, unitsLost;
    private final Number[] arguments;

    private ObjectiveResult(GameModel model, String code, Number... arguments) {
        this.code = code;
        this.arguments = arguments.clone();
        objectiveType = model.objectiveType;
        outcome = model.outcome;
        terminalReason = model.terminalReason;
        completed = outcome == GameModel.WON;
        historical = objectiveType != Challenge.CAMPAIGN && !revised(model);
        policy = policy(model);
        elapsedSeconds = model.elapsed;
        actualSeconds = objectiveType == Challenge.KEEP_KING ? model.elapsed : model.objectiveProgress;
        requiredSeconds = model.objectiveSeconds;
        unitsSent = model.unitsSent;
        budget = model.deploymentBudget;
        remainingAllowance = Math.max(0, budget - unitsSent);
        captures = model.captures;
        unitsLost = model.unitsLost;
    }

    /** Arguments are numbers, not translated or preformatted text, and returned defensively. */
    public Number[] numericArguments() { return arguments.clone(); }

    public boolean hasFastestTime() { return policy == Policy.ELAPSED_TIME || policy == Policy.CAMPAIGN; }

    public static boolean revised(GameModel model) {
        return model != null && revised(model.objectiveType, model.rulesVersion, model.missionConfigVersion);
    }

    private static boolean revised(int type, int rules, int config) {
        return type >= Challenge.HOLD_KING && type <= Challenge.BUDGET && config > 0
            && (rules == 11 || rules == 10 && config >= 2);
    }

    public static Policy policy(GameModel model) {
        if (model == null) return Policy.NONE;
        return policy(model.objectiveType, model.rulesVersion, model.missionConfigVersion);
    }

    public static Policy policy(int type, int rules, int config) {
        if (type == Challenge.CAMPAIGN) return Policy.CAMPAIGN;
        if (type < Challenge.HOLD_KING || type > Challenge.BUDGET) return Policy.NONE;
        if (!revised(type, rules, config))
            return rules == 10 && config >= 0 && config <= 1 ? Policy.HISTORICAL : Policy.NONE;
        if (type == Challenge.HOLD_KING) return Policy.ELAPSED_TIME;
        if (type == Challenge.KEEP_KING) return Policy.COMPLETION_ONLY;
        return Policy.DEPLOYED_TROOPS;
    }

    /** Do not infer an old defeat's cause from counters or the current board. */
    public static ObjectiveResult evaluate(GameModel model) {
        if (model == null) throw new IllegalArgumentException("Missing result model");
        if (model.outcome == GameModel.PLAYING) return new ObjectiveResult(model, IN_PROGRESS);
        if (model.outcome == GameModel.WON) {
            if (model.objectiveType == Challenge.HOLD_KING)
                return new ObjectiveResult(model, HOLD_COMPLETED,
                    model.elapsed, model.objectiveProgress, model.objectiveSeconds);
            if (model.objectiveType == Challenge.KEEP_KING)
                return new ObjectiveResult(model, KEEP_COMPLETED, model.elapsed, model.objectiveSeconds);
            if (model.objectiveType == Challenge.BUDGET)
                return new ObjectiveResult(model, BUDGET_COMPLETED,
                    model.unitsSent, model.deploymentBudget, Math.max(0, model.deploymentBudget - model.unitsSent));
            return new ObjectiveResult(model, CAMPAIGN_WON);
        }
        switch (model.terminalReason) {
            case GameModel.TERMINAL_BUDGET:
                return new ObjectiveResult(model, BUDGET_EXCEEDED, model.unitsSent, model.deploymentBudget);
            case GameModel.TERMINAL_PROTECTED_KING: return new ObjectiveResult(model, PROTECTED_KING_LOST);
            case GameModel.TERMINAL_ELIMINATED: return new ObjectiveResult(model, PLAYER_ELIMINATED);
            case GameModel.TERMINAL_SURRENDER: return new ObjectiveResult(model, SURRENDERED);
            default: return new ObjectiveResult(model, LEGACY_DEFEAT);
        }
    }
}
