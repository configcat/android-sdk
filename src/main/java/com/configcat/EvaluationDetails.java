package com.configcat;

/**
 * Additional information about flag evaluation.
 */
public class EvaluationDetails<T> {
    private final T value;
    private final String key;
    private final String variationId;
    private final User user;
    private final boolean isDefaultValue;
    private final String error;
    private final long fetchTimeUnixMilliseconds;
    private final RolloutRule matchedTargetingRule;
    private final PercentageRule matchedPercentageOption;

    public EvaluationDetails(T value,
                             String key,
                             String variationId,
                             User user,
                             boolean isDefaultValue,
                             String error,
                             long fetchTimeUnixMilliseconds,
                             RolloutRule matchedTargetingRule,
                             PercentageRule matchedPercentageOption) {
        this.value = value;
        this.key = key;
        this.variationId = variationId;
        this.user = user;
        this.isDefaultValue = isDefaultValue;
        this.error = error;
        this.fetchTimeUnixMilliseconds = fetchTimeUnixMilliseconds;
        this.matchedTargetingRule = matchedTargetingRule;
        this.matchedPercentageOption = matchedPercentageOption;
    }

    static <T> EvaluationDetails<T> fromError(String key, T defaultValue, String error, User user) {
        return new EvaluationDetails<>(defaultValue, key,"", user, true, error, Constants.DISTANT_PAST, null, null);
    }

    <TR> EvaluationDetails<TR> asTypeSpecific() {
        return new EvaluationDetails<>((TR)value, key, variationId, user, isDefaultValue, error, fetchTimeUnixMilliseconds, matchedTargetingRule, matchedPercentageOption);
    }

    /**
     * The evaluated value of the feature flag or setting.
     */
    public T getValue() {
        return value;
    }

    /**
     * The key of the evaluated feature flag or setting.
     */
    public String getKey() {
        return key;
    }

    /**
     * The variationID is the identifier of the evaluated value. Usually used for analytics.
     */
    public String getVariationId() {
        return variationId;
    }

    /**
     * The user object that was used for evaluation.
     */
    public User getUser() {
        return user;
    }

    /**
     * True when the default value was returned, possibly due to an error.
     */
    public boolean isDefaultValue() {
        return isDefaultValue;
    }

    /**
     * In case of an error, this field contains the error message.
     */
    public String getError() {
        return error;
    }

    /**
     * The last download time of the current config in unix milliseconds format.
     */
    public Long getFetchTimeUnixMilliseconds() {
        return fetchTimeUnixMilliseconds;
    }

    /**
     * If the evaluation was based on a targeting rule, this field contains that specific rule.
     */
    public RolloutRule getMatchedTargetingRule() {
        return matchedTargetingRule;
    }

    /**
     * If the evaluation was based on a percentage rule, this field contains that specific rule.
     */
    public PercentageRule getMatchedPercentageOption() {
        return matchedPercentageOption;
    }
}