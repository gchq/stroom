package stroom.authentication.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class PasswordIntegrityChecksConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    private StroomDuration neverUsedAccountDeactivationThreshold = StroomDuration.ofDays(30);

    @NotNull
    @JsonProperty
    private StroomDuration unusedAccountDeactivationThreshold = StroomDuration.ofDays(90);

    @NotNull
    @JsonProperty
    private StroomDuration mandatoryPasswordChangeDuration = StroomDuration.ofDays(90);

//    @NotNull
//    @JsonProperty
//    private StroomDuration durationBetweenChecks = StroomDuration.ofMinutes(2);

    @JsonProperty
    private boolean forcePasswordChangeOnFirstLogin = true;

    @ValidRegex
    @JsonProperty
    @JsonPropertyDescription("A regex pattern that new passwords must match")
    // The default is to let everything through
    private String passwordComplexityRegex = ".*";

    @Min(0)
    @NotNull
    @JsonProperty
    private int minimumPasswordLength;

    public StroomDuration getNeverUsedAccountDeactivationThreshold() {
        return neverUsedAccountDeactivationThreshold;
    }

    @SuppressWarnings("unused")
    public void setNeverUsedAccountDeactivationThreshold(final StroomDuration neverUsedAccountDeactivationThreshold) {
        this.neverUsedAccountDeactivationThreshold = neverUsedAccountDeactivationThreshold;
    }

    public StroomDuration getUnusedAccountDeactivationThreshold() {
        return unusedAccountDeactivationThreshold;
    }

    @SuppressWarnings("unused")
    public void setUnusedAccountDeactivationThreshold(final StroomDuration unusedAccountDeactivationThreshold) {
        this.unusedAccountDeactivationThreshold = unusedAccountDeactivationThreshold;
    }

    public StroomDuration getMandatoryPasswordChangeDuration() {
        return mandatoryPasswordChangeDuration;
    }

    @SuppressWarnings("unused")
    public void setMandatoryPasswordChangeDuration(final StroomDuration mandatoryPasswordChangeDuration) {
        this.mandatoryPasswordChangeDuration = mandatoryPasswordChangeDuration;
    }

//    public StroomDuration getDurationBetweenChecks() {
//        return durationBetweenChecks;
//    }
//
//    @SuppressWarnings("unused")
//    public void setDurationBetweenChecks(final StroomDuration durationBetweenChecks) {
//        this.durationBetweenChecks = durationBetweenChecks;
//    }

    public boolean isForcePasswordChangeOnFirstLogin() {
        return forcePasswordChangeOnFirstLogin;
    }

    @SuppressWarnings("unused")
    public void setForcePasswordChangeOnFirstLogin(final boolean forcePasswordChangeOnFirstLogin) {
        this.forcePasswordChangeOnFirstLogin = forcePasswordChangeOnFirstLogin;
    }

    public String getPasswordComplexityRegex() {
        return passwordComplexityRegex;
    }

    @SuppressWarnings("unused")
    public void setPasswordComplexityRegex(final String passwordComplexityRegex) {
        this.passwordComplexityRegex = passwordComplexityRegex;
    }

    public int getMinimumPasswordLength() {
        return minimumPasswordLength;
    }

    @SuppressWarnings("unused")
    public void setMinimumPasswordLength(final int minimumPasswordLength) {
        this.minimumPasswordLength = minimumPasswordLength;
    }
}
