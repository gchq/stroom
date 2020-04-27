package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.time.StroomDuration;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class PasswordIntegrityChecksConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    private StroomDuration neverUsedAccountDeactivationThreshold = StroomDuration.parse("P30D");

    @NotNull
    @JsonProperty
    private StroomDuration unusedAccountDeactivationThreshold = StroomDuration.parse("P90D");

    @NotNull
    @JsonProperty
    private StroomDuration mandatoryPasswordChangeDuration = StroomDuration.parse("P90D");

    @NotNull
    @JsonProperty
    private StroomDuration durationBetweenChecks = StroomDuration.parse("PT2M");

    @NotNull
    @JsonProperty
    private boolean forcePasswordChangeOnFirstLogin = true;

    @ValidRegex
    @JsonProperty
    // The default is to let everything through
    private String passwordComplexityRegex = ".*";

    @Min(0)
    @NotNull
    @JsonProperty
    private int minimumPasswordLength;

    public StroomDuration getNeverUsedAccountDeactivationThreshold() {
        return neverUsedAccountDeactivationThreshold;
    }

    public void setNeverUsedAccountDeactivationThreshold(final StroomDuration neverUsedAccountDeactivationThreshold) {
        this.neverUsedAccountDeactivationThreshold = neverUsedAccountDeactivationThreshold;
    }

    public StroomDuration getUnusedAccountDeactivationThreshold() {
        return unusedAccountDeactivationThreshold;
    }

    public void setUnusedAccountDeactivationThreshold(final StroomDuration unusedAccountDeactivationThreshold) {
        this.unusedAccountDeactivationThreshold = unusedAccountDeactivationThreshold;
    }

    public StroomDuration getMandatoryPasswordChangeDuration() {
        return mandatoryPasswordChangeDuration;
    }

    public void setMandatoryPasswordChangeDuration(final StroomDuration mandatoryPasswordChangeDuration) {
        this.mandatoryPasswordChangeDuration = mandatoryPasswordChangeDuration;
    }

    public StroomDuration getDurationBetweenChecks() {
        return durationBetweenChecks;
    }

    public void setDurationBetweenChecks(final StroomDuration durationBetweenChecks) {
        this.durationBetweenChecks = durationBetweenChecks;
    }

    public boolean isForcePasswordChangeOnFirstLogin() {
        return forcePasswordChangeOnFirstLogin;
    }

    public void setForcePasswordChangeOnFirstLogin(final boolean forcePasswordChangeOnFirstLogin) {
        this.forcePasswordChangeOnFirstLogin = forcePasswordChangeOnFirstLogin;
    }

    public String getPasswordComplexityRegex() {
        return passwordComplexityRegex;
    }

    public void setPasswordComplexityRegex(final String passwordComplexityRegex) {
        this.passwordComplexityRegex = passwordComplexityRegex;
    }

    public int getMinimumPasswordLength() {
        return minimumPasswordLength;
    }

    public void setMinimumPasswordLength(final int minimumPasswordLength) {
        this.minimumPasswordLength = minimumPasswordLength;
    }
}
