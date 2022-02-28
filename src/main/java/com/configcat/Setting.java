package com.configcat;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Describes a ConfigCat Feature Flag / Setting
 */
public class Setting {
    /**
     * Value of the feature flag / setting.
     */
    @SerializedName(value = "v")
    public JsonElement value;

    /**
     * Type of the feature flag / setting.
     *
     * 0: [bool],
     * 1: [String],
     * 2: [int],
     * 3: [double],
     */
    @SerializedName(value = "t")
    public int type;

    /**
     * Collection of percentage rules that belongs to the feature flag / setting.
     */
    @SerializedName(value = "p")
    public RolloutPercentageItem[] percentageItems;

    /**
     * Collection of targeting rules that belongs to the feature flag / setting.
     */
    @SerializedName(value = "r")
    public RolloutRule[] rolloutRules;

    /**
     * Variation ID (for analytical purposes).
     */
    @SerializedName(value = "i")
    public String variationId = "";
}
