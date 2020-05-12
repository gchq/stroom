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
    @JsonPropertyDescription("Unused user accounts with a duration since account creation greater than this " +
            "value will be locked. The frequency of checks is " +
            "controlled by the job 'Account Maintenance'.")
    private StroomDuration neverUsedAccountDeactivationThreshold = StroomDuration.ofDays(30);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("User accounts with a duration since last login greater than this " +
            "value will be locked. The frequency of checks is " +
            "controlled by the job 'Account Maintenance'.")
    private StroomDuration unusedAccountDeactivationThreshold = StroomDuration.ofDays(90);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The age after which a password will have to be changed. The frequency of checks is " +
            "controlled by the job 'Account Maintenance'.")
    private StroomDuration mandatoryPasswordChangeDuration = StroomDuration.ofDays(90);

//    @NotNull
//    @JsonProperty
//    private StroomDuration durationBetweenChecks = StroomDuration.ofMinutes(2);

    @JsonProperty
    @JsonPropertyDescription("If true, on first login the user will be forced to change their password.")
    private boolean forcePasswordChangeOnFirstLogin = true;

    @ValidRegex
    @JsonProperty
    @JsonPropertyDescription("A regex pattern that new passwords must match")
    // The default is to let everything through
    private String passwordComplexityRegex = ".*";

    @Min(0)
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The minimum number of characters that new passwords need to contain.")
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
