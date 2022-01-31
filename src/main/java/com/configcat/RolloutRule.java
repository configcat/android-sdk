package com.configcat;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class RolloutRule {
    /**
     * Value served when the rule is selected during evaluation.
     */
    @SerializedName(value = "v")
    public JsonElement value;

    /**
     * The user attribute used in the comparison during evaluation.
     */
    @SerializedName(value = "a")
    public String comparisonAttribute;

    /**
     * The operator used in the comparison.
     *
     * 0  -> 'IS ONE OF',
     * 1  -> 'IS NOT ONE OF',
     * 2  -> 'CONTAINS',
     * 3  -> 'DOES NOT CONTAIN',
     * 4  -> 'IS ONE OF (SemVer)',
     * 5  -> 'IS NOT ONE OF (SemVer)',
     * 6  -> '< (SemVer)',
     * 7  -> '<= (SemVer)',
     * 8  -> '> (SemVer)',
     * 9  -> '>= (SemVer)',
     * 10 -> '= (Number)',
     * 11 -> '<> (Number)',
     * 12 -> '< (Number)',
     * 13 -> '<= (Number)',
     * 14 -> '> (Number)',
     * 15 -> '>= (Number)',
     * 16 -> 'IS ONE OF (Sensitive)',
     * 17 -> 'IS NOT ONE OF (Sensitive)'
     */
    @SerializedName(value = "t")
    public int comparator;

    /**
     * The comparison value compared to the given user attribute.
     */
    @SerializedName(value = "c")
    public String comparisonValue;

    /**
     * The rule's variation ID (for analytical purposes).
     */
    @SerializedName(value = "i")
    public String variationId;
}
