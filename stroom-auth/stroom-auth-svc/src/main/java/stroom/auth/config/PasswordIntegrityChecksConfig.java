package stroom.auth.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.validation.constraints.NotNull;

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

    @JsonProperty
    // The default is to let everything through
    private String passwordComplexityRegex = ".*";

    @NotNull
    @JsonProperty
    private int minimumPasswordLength;

    public StroomDuration getNeverUsedAccountDeactivationThreshold() {
        return neverUsedAccountDeactivationThreshold;
    }

    public StroomDuration getUnusedAccountDeactivationThreshold() {
        return unusedAccountDeactivationThreshold;
    }

    public StroomDuration getMandatoryPasswordChangeDuration() {
        return mandatoryPasswordChangeDuration;
    }

    public StroomDuration getDurationBetweenChecks() {
        return durationBetweenChecks;
    }

    public boolean isForcePasswordChangeOnFirstLogin() {
        return forcePasswordChangeOnFirstLogin;
    }

    public String getPasswordComplexityRegex() {
        return passwordComplexityRegex;
    }

    public int getMinimumPasswordLength() {
        return minimumPasswordLength;
    }
}
