package stroom.security.shared;

import stroom.util.shared.StringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class PermissionChangeImpactSummary {

    private static final PermissionChangeImpactSummary EMPTY = new PermissionChangeImpactSummary(
            null, null);

    @JsonProperty
    private final String impactSummary;
    @JsonProperty
    private final String impactDetail;

    @JsonCreator
    public PermissionChangeImpactSummary(
            @JsonProperty("impactSummary") final String impactSummary,
            @JsonProperty("impactDetail") final String impactDetail) {

        this.impactSummary = StringUtil.blankAsNull(impactSummary);
        this.impactDetail = StringUtil.blankAsNull(impactDetail);
    }

    public static PermissionChangeImpactSummary empty() {
        return EMPTY;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public String getImpactDetail() {
        return impactDetail;
    }

    @Override
    public String toString() {
        return "PermissionChangeImpactSummary{" +
                ", impactSummary='" + impactSummary + '\'' +
                ", impactDetail='" + impactDetail + '\'' +
                '}';
    }
}
