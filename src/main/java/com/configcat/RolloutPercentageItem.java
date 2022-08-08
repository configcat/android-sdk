package com.configcat;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Describes a rollout percentage rule.
 */
public class RolloutPercentageItem {
    /**
     * Value served when the rule is selected during evaluation.
     */
    @SerializedName(value = "v")
    public JsonElement value;

    /**
     * The rule's percentage value.
     */
    @SerializedName(value = "p")
    public double percentage;

    /**
     * The rule's variation ID (for analytical purposes).
     */
    @SerializedName(value = "i")
    public String variationId;
}
