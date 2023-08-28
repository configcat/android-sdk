package com.configcat;

import com.google.gson.JsonElement;
import de.skuzzle.semantic.Version;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.binary.Hex;

import java.util.*;

class EvaluationResult {
    public final SettingsValue value;
    public final String variationId;
    public final TargetingRule targetingRule;
    public final PercentageOption percentageOption;

    EvaluationResult(SettingsValue value, String variationId, TargetingRule targetingRule, PercentageOption percentageOption) {
        this.value = value;
        this.variationId = variationId;
        this.targetingRule = targetingRule;
        this.percentageOption = percentageOption;
    }
}

class RolloutEvaluator {

    private final ConfigCatLogger logger;

    public RolloutEvaluator(ConfigCatLogger logger) {
        this.logger = logger;
    }

    public EvaluationResult evaluate(Setting setting, String key, User user) {
        //TODO logger is not need to run if log level is INFO? check the trick
        EvaluateLogger evaluateLogger = new EvaluateLogger(key);

        try {

            if (user == null) {
                //TODO handle missing user logging. based on changes.
                if ((setting.getTargetingRules() != null && setting.getTargetingRules().length > 0) ||
                        (setting.getPercentageOptions() != null && setting.getPercentageOptions().length > 0)) {
                    this.logger.warn(3001, ConfigCatLogMessages.getTargetingIsNotPossible(key));
                }

                evaluateLogger.logReturnValue(setting.getSettingsValue().toString());
                return new EvaluationResult(setting.getSettingsValue(), setting.getVariationId(), null, null);
            }

            evaluateLogger.logUserObject(user);

            if (setting.getTargetingRules() != null) {
                EvaluationResult targetingRulesEvaluationResult = evaluateTargetingRules(setting, user, key, evaluateLogger);
                if (targetingRulesEvaluationResult != null) return targetingRulesEvaluationResult;
            }

            if (setting.getPercentageOptions() != null && setting.getPercentageOptions().length > 0) {
                EvaluationResult percentageOptionsEvaluationResult = evaluatePercentageOptions(setting.getPercentageOptions(), setting.getPercentageAttribute(), key, user, null, evaluateLogger);
                if (percentageOptionsEvaluationResult != null) return percentageOptionsEvaluationResult;
            }

            evaluateLogger.logReturnValue(setting.getSettingsValue().toString());
            return new EvaluationResult(setting.getSettingsValue(), setting.getVariationId(), null, null);
        } finally {
            this.logger.info(5000, evaluateLogger.toPrint());
        }
    }

    private EvaluationResult evaluateTargetingRules(Setting setting, User user, String key, EvaluateLogger evaluateLogger) {
        //TODO evaluation context should be added?
        //TODO logger eval targeting rules apply first ....

        for (TargetingRule rule : setting.getTargetingRules()) {

            if (!evaluateConditions(rule.getConditions(), user, setting.getConfigSalt(), key, evaluateLogger)) {
                continue;
            }
            // Conditions match, if rule.getServedValue() not null. we shuold return as logMatch value from SV
            //if no SV then PO should be aviable
            if (rule.getServedValue() != null) {
                return new EvaluationResult(rule.getServedValue().getValue(), rule.getServedValue().getVariationId(), rule, null);
            }
            //if (PO.lenght <= 0) error case no SV and no PO
            if (rule.getPercentageOptions() == null || rule.getPercentageOptions().length == 0) {
                //TODO error? log something?
                continue;
            }
            return evaluatePercentageOptions(rule.getPercentageOptions(), setting.getPercentageAttribute(), key, user, rule, evaluateLogger);

        }
        //TODO loogging should be reworked.
        // evaluateLogger.logNoMatch(comparisonAttribute, userValue, comparator, comparisonCondition);
        return null;
    }

    private boolean evaluateConditions(Condition[] conditions, User user, String configSalt, String key, EvaluateLogger evaluateLogger) {
        //Conditions are ANDs so if One is not matching return false, if all matching return true
        //TODO rework logging based on changes possibly
        boolean conditionsEvaluationResult = false;
        for (Condition condition : conditions) {
            //TODO log IF, AND based on order

            //TODO Condition, what if condition invalid? more then one condition added or none. rework basic if
            if (condition.getComparisonCondition() != null) {
                conditionsEvaluationResult = evaluateComparisonCondition(condition.getComparisonCondition(), user, configSalt, key, evaluateLogger);
            } else if (condition.getSegmentCondition() != null) {
                //TODO evalSC
                conditionsEvaluationResult = evaluateSegmentCondition(condition.getSegmentCondition());
            } else if (condition.getPrerequisiteFlagCondition() != null) {
                conditionsEvaluationResult = evaluatePrerequisiteFlagCondition(condition.getPrerequisiteFlagCondition());
                //TODO evalPFC
            }
            // else throw Some exception here?
            if (!conditionsEvaluationResult) {
                //TODO no match for the TR. LOG and go to the next one?
                //TODO this should be return from a condEvalMethod
                return false;
            }
        }
        return conditionsEvaluationResult;
    }

    private boolean evaluateComparisonCondition(ComparisonCondition comparisonCondition, User user, String configSalt, String key, EvaluateLogger evaluateLogger) {
        String comparisonAttribute = comparisonCondition.getComparisonAttribute();
        Comparator comparator = Comparator.fromId(comparisonCondition.getComparator());
        String userValue = user.getAttribute(comparisonAttribute);

        //TODO Check if all value available. User missing is separated handle in every Condition checks? cc/sc/pfc
        //TODO what if CV value is not the right one for the comparator?  How to hand,e CV missing? etc.
        // if (comparisonValue == null || comparisonValue.isEmpty() ||
        if (userValue == null || userValue.isEmpty()) {
            //evaluateLogger.logNoMatch(comparisonAttribute, userValue, comparator, comparisonValue);
            return false;
        }

        //TODO comparator null? error?
        //TODO in case of exception catch return false. is this oK?
        switch (comparator) {
            //TODO log match should be handled on return and just for the TR?
            // evaluateLogger.logMatch(comparisonAttribute, userValue, comparator, containsValues, value);
            case CONTAINS_ANY_OF:
                List<String> containsValues = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; containsValues.size() > index; index++){
                    containsValues.set(index, containsValues.get(index).trim());
                }
                containsValues.removeAll(Arrays.asList(null, ""));
                for (String containsValue : containsValues) {
                    if (userValue.contains(containsValue))
                        return true;
                }
                return false;
            case NOT_CONTAINS_ANY_OF:
                List<String> notContainsValues = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; notContainsValues.size() > index; index++){
                    notContainsValues.set(index, notContainsValues.get(index).trim());
                }
                notContainsValues.removeAll(Arrays.asList(null, ""));
                for (String notcontainsValue : notContainsValues) {
                    if (userValue.contains(notcontainsValue))
                        return false;
                }
                return true;
            case SEMVER_IS_ONE_OF:
            case SEMVER_IS_NOT_ONE_OF:
                List<String> inSemVerValues = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; inSemVerValues.size() > index; index++){
                    inSemVerValues.set(index, inSemVerValues.get(index).trim());
                }
                inSemVerValues.removeAll(Arrays.asList(null, ""));
                try {
                    Version userVersion = Version.parseVersion(userValue.trim(), true);
                    boolean matched = false;
                    for (String semVer : inSemVerValues) {
                        matched = userVersion.compareTo(Version.parseVersion(semVer, true)) == 0 || matched;
                    }

                    return (matched && Comparator.SEMVER_IS_ONE_OF.equals(comparator)) || (!matched && Comparator.SEMVER_IS_NOT_ONE_OF.equals(comparator));
                } catch (Exception e) {
                    String message = evaluateLogger.logFormatError(comparisonAttribute, userValue, comparator, inSemVerValues, e);
                    this.logger.warn(0, message);
                    return false;
                }
            case SEMVER_LESS:
            case SEMVER_LESS_EQULAS:
            case SEMVER_GREATER:
            case SEMVER_GREATER_EQUALS:
                try {
                    Version cmpUserVersion = Version.parseVersion(userValue.trim(), true);
                    String comparisonValue = comparisonCondition.getStringValue();
                    Version matchValue = Version.parseVersion(comparisonValue.trim(), true);
                    return (Comparator.SEMVER_LESS.equals(comparator) && cmpUserVersion.isLowerThan(matchValue)) ||
                            (Comparator.SEMVER_LESS_EQULAS.equals(comparator) && cmpUserVersion.compareTo(matchValue) <= 0) ||
                            (Comparator.SEMVER_GREATER.equals(comparator) && cmpUserVersion.isGreaterThan(matchValue)) ||
                            (Comparator.SEMVER_GREATER_EQUALS.equals(comparator) && cmpUserVersion.compareTo(matchValue) >= 0);
                } catch (Exception e) {
                    String message = evaluateLogger.logFormatError(comparisonAttribute, userValue, comparator, comparisonCondition.getStringValue(), e);
                    this.logger.warn(0, message);
                    return false;
                }
            case NUMBER_EQUALS:
            case NUMBER_NOT_EQUALS:
            case NUMBER_LESS:
            case NUMBER_LESS_EQUALS:
            case NUMBER_GREATER:
            case NUMBER_GREATER_EQUALS:
                try {
                    Double userDoubleValue = Double.parseDouble(userValue.trim().replaceAll(",", "."));
                    Double comparisonDoubleValue = comparisonCondition.getDoubleValue();

                    return (Comparator.NUMBER_EQUALS.equals(comparator) && userDoubleValue.equals(comparisonDoubleValue)) ||
                            (Comparator.NUMBER_NOT_EQUALS.equals(comparator) && !userDoubleValue.equals(comparisonDoubleValue)) ||
                            (Comparator.NUMBER_LESS.equals(comparator) && userDoubleValue < comparisonDoubleValue) ||
                            (Comparator.NUMBER_LESS_EQUALS.equals(comparator) && userDoubleValue <= comparisonDoubleValue) ||
                            (Comparator.NUMBER_GREATER.equals(comparator) && userDoubleValue > comparisonDoubleValue) ||
                            (Comparator.NUMBER_GREATER_EQUALS.equals(comparator) && userDoubleValue >= comparisonDoubleValue);
                } catch (NumberFormatException e) {
                    String message = evaluateLogger.logFormatError(comparisonAttribute, userValue, comparator, comparisonCondition.getDoubleValue(), e);
                    this.logger.warn(0, message);
                    return false;
                }
            case SENSITIVE_IS_ONE_OF:
                //TODO salt error handle
                List<String> inValuesSensitive = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; inValuesSensitive.size() > index; index++){
                    inValuesSensitive.set(index, inValuesSensitive.get(index).trim());
                }
                inValuesSensitive.removeAll(Arrays.asList(null, ""));
                String hashValueOne = getSaltedUserValue(userValue, configSalt, key);
                return inValuesSensitive.contains(hashValueOne);
            case SENSITIVE_IS_NOT_ONE_OF:
                //TODO add salt and salt error handle
                List<String> notInValuesSensitive = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; notInValuesSensitive.size() > index; index++){
                    notInValuesSensitive.set(index, notInValuesSensitive.get(index).trim());
                }
                notInValuesSensitive.removeAll(Arrays.asList(null, ""));
                String hashValueNotOne = getSaltedUserValue(userValue, configSalt, key);
                return !notInValuesSensitive.contains(hashValueNotOne);
            case DATE_BEFORE:
            case DATE_AFTER:
                try {
                    Double userDoubleValue = Double.parseDouble(userValue.trim().replaceAll(",", "."));
                    Double comparisonDoubleValue = comparisonCondition.getDoubleValue();
                    return (Comparator.DATE_BEFORE.equals(comparator) && userDoubleValue < comparisonDoubleValue) ||
                            (Comparator.DATE_AFTER.equals(comparator) && userDoubleValue > comparisonDoubleValue);
                } catch (NumberFormatException e) {
                    //TODO add new error handling to Date '{userAttributeValue}' is not a valid Unix timestamp (number of seconds elapsed since Unix epoch)
                    String message = evaluateLogger.logFormatError(comparisonAttribute, userValue, comparator, comparisonCondition.getDoubleValue(), e);
                    this.logger.warn(0, message);
                    return false;
                }
            case HASHED_EQUALS:
                //TODO add salt and salt error handle
                String hashEquals = getSaltedUserValue(userValue, configSalt, key);
                return hashEquals.equals(comparisonCondition.getStringValue());
            case HASHED_NOT_EQUALS:
                //TODO add salt and salt error handle
                String hashNotEquals = getSaltedUserValue(userValue, configSalt, key);
                return !hashNotEquals.equals(comparisonCondition.getStringValue());
            case HASHED_STARTS_WITH:
            case HASHED_ENDS_WITH:
            case HASHED_NOT_STARTS_WITH:
            case HASHED_NOT_ENDS_WITH:
                //TODO add salt and salt error handle
                List<String> withValues = new ArrayList<>(Arrays.asList(comparisonCondition.getStringArrayValue()));
                for(int index = 0; withValues.size() > index; index++){
                    withValues.set(index, withValues.get(index).trim());
                }
                withValues.removeAll(Arrays.asList(null, ""));
                boolean foundEqual = false;
                for (String comparisonValueHashedStartsEnds : withValues) {
                    int indexOf = comparisonValueHashedStartsEnds.indexOf("_");
                    //TODO is it false or skip
                    if (indexOf <= 0) {
                        return false;
                    }
                    String comparedTextLength = comparisonValueHashedStartsEnds.substring(0, indexOf);
                    try {
                        int comparedTextLengthInt = Integer.parseInt(comparedTextLength);
                        if (userValue.length() < comparedTextLengthInt) {
                            return false;
                        }
                        String comparisonHashValue = comparisonValueHashedStartsEnds.substring(indexOf + 1);
                        if (comparisonHashValue.isEmpty()) {
                            return false;
                        }
                        String userValueSubString;
                        if (Comparator.HASHED_STARTS_WITH.equals(comparator) || Comparator.HASHED_NOT_STARTS_WITH.equals(comparator)) {
                            userValueSubString = userValue.substring(0, comparedTextLengthInt);
                        } else { //HASHED_ENDS_WITH
                            userValueSubString = userValue.substring(userValue.length() - comparedTextLengthInt);
                        }
                        String hashUserValueSub = getSaltedUserValue(userValueSubString, configSalt, key);
                        if(hashUserValueSub.equals(comparisonHashValue)){
                            foundEqual = true;
                        }
                    } catch (NumberFormatException e) {
                        String message = evaluateLogger.logFormatError(comparisonAttribute, userValue, comparator, comparisonValueHashedStartsEnds, e);
                        this.logger.warn(0, message);
                        return false;
                    }
                }
                if (Comparator.HASHED_NOT_STARTS_WITH.equals(comparator) || Comparator.HASHED_NOT_ENDS_WITH.equals(comparator)) {
                    return !foundEqual;
                }
                return foundEqual;
            case HASHED_ARRAY_CONTAINS:
                //TODO add salt and salt error handle
                String[] userCSVContainsHashSplit = userValue.split(",");
                if (userCSVContainsHashSplit.length == 0) {
                    return false;
                }
                for (String userValueSlice : userCSVContainsHashSplit) {
                    String userValueSliceHash = getSaltedUserValue(userValueSlice, configSalt, key);
                    if (userValueSliceHash.equals(comparisonCondition.getStringValue())) {
                        return true;
                    }
                }
                return false;
            case HASHED_ARRAY_NOT_CONTAINS:
                //TODO add salt and salt error handle
                String[] userCSVNotContainsHashSplit = userValue.split(",");
                if (userCSVNotContainsHashSplit.length == 0) {
                    return false;
                }
                boolean containsFlag = false;
                for (String userValueSlice : userCSVNotContainsHashSplit) {
                    String userValueSliceHash = getSaltedUserValue(userValueSlice, configSalt, key);
                    if (userValueSliceHash.equals(comparisonCondition.getStringValue())) {
                        containsFlag = true;
                    }
                }
                return !containsFlag;

        }
        return true;
    }

    private static String getSaltedUserValue(String userValue, String configJsonSalt, String key) {
        return new String(Hex.encodeHex(DigestUtils.sha256(userValue + configJsonSalt + key)));
    }

    private boolean evaluateSegmentCondition(SegmentCondition segmentCondition) {
        //TODO implement
        return true;
    }

    private boolean evaluatePrerequisiteFlagCondition(PrerequisiteFlagCondition prerequisiteFlagCondition) {
        //TODO implement
        return true;
    }

    private static EvaluationResult evaluatePercentageOptions(PercentageOption[] percentageOptions, String percentageOptionAttribute, String key, User user, TargetingRule parentTargetingRule, EvaluateLogger evaluateLogger) {
        //TODO if user missing? based on .net skipp should be logged here
        String percentageOptionAttributeValue;
        String percentageOptionAttributeName = percentageOptionAttribute;
        if (percentageOptionAttributeName == null || percentageOptionAttributeName.isEmpty()) {
            percentageOptionAttributeName = "Identifier";
            percentageOptionAttributeValue = user.getIdentifier();
        } else {
            percentageOptionAttributeValue = user.getAttribute(percentageOptionAttributeName);
            if (percentageOptionAttributeValue == null) {
                //TODO log skip beacuse atribute value missing
                return null;
            }
        }
        //TODO log misisng Evalu % option based on .....
        //TODO salt must be added?
        String hashCandidate = key + percentageOptionAttributeValue;
        int scale = 100;
        String hexHash = new String(Hex.encodeHex(DigestUtils.sha1(hashCandidate))).substring(0, 7);
        int longHash = Integer.parseInt(hexHash, 16);
        int scaled = longHash % scale;

        int bucket = 0;
        for (PercentageOption rule : percentageOptions) {

            bucket += rule.getPercentage();
            if (scaled < bucket) {
                evaluateLogger.logPercentageEvaluationReturnValue(rule.getValue().toString());
                return new EvaluationResult(rule.getValue(), rule.getVariationId(), parentTargetingRule, rule);
            }
        }

        return null;
    }


}
