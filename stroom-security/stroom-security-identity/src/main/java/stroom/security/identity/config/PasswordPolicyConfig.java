package stroom.security.identity.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidRegex;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class PasswordPolicyConfig extends AbstractConfig implements IsStroomConfig {

    public static final boolean DEFAULT_ALLOW_PASSWORD_RESETS = true;
    public static final StroomDuration DEFAULT_NEVER_USED_ACCOUNT_DEACTIVATION_THRESHOLD = StroomDuration.ofDays(30);
    public static final StroomDuration DEFAULT_UNUSED_ACCOUNT_DEACTIVATION_THRESHOLD = StroomDuration.ofDays(90);
    public static final StroomDuration DEFAULT_MANDATORY_PASSWORD_CHANGE_DURATION = StroomDuration.ofDays(90);
    public static final boolean DEFAULT_FORCE_PASSWORD_CHANGE_ON_FIRST_LOGIN = true;
    public static final String DEFAULT_PASSWORD_COMPLEXITY_REGEX = ".*";
    public static final int DEFAULT_MINIMUM_PASSWORD_STRENGTH = 3;
    public static final int DEFAULT_MINIMUM_PASSWORD_LENGTH = 8;

    @JsonProperty
    @JsonPropertyDescription("Will the UI allow password resets")
    private final boolean allowPasswordResets;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Unused user accounts with a duration since account creation greater than this " +
                             "value will be locked. The frequency of checks is " +
                             "controlled by the job 'Account Maintenance'.")
    private final StroomDuration neverUsedAccountDeactivationThreshold;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("User accounts with a duration since last login greater than this " +
                             "value will be locked. The frequency of checks is " +
                             "controlled by the job 'Account Maintenance'.")
    private final StroomDuration unusedAccountDeactivationThreshold;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The age after which a password will have to be changed. The frequency of checks is " +
                             "controlled by the job 'Account Maintenance'.")
    private final StroomDuration mandatoryPasswordChangeDuration;

    @JsonProperty
    @JsonPropertyDescription("If true, on first login the user will be forced to change their password.")
    private final boolean forcePasswordChangeOnFirstLogin;

    @ValidRegex
    @JsonProperty
    @JsonPropertyDescription("A regex pattern that new passwords must match")
    // The default is to let everything through
    private final String passwordComplexityRegex;

    @Min(0)
    @Max(5)
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The minimum strength password that is allowed.")
    private final Integer minimumPasswordStrength;

    @Min(0)
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The minimum number of characters that new passwords need to contain.")
    private final Integer minimumPasswordLength;

    @JsonProperty
    @JsonPropertyDescription("A message informing users of the password policy")
    private final String passwordPolicyMessage;

    public PasswordPolicyConfig() {
        allowPasswordResets = DEFAULT_ALLOW_PASSWORD_RESETS;
        neverUsedAccountDeactivationThreshold = DEFAULT_NEVER_USED_ACCOUNT_DEACTIVATION_THRESHOLD;
        unusedAccountDeactivationThreshold = DEFAULT_UNUSED_ACCOUNT_DEACTIVATION_THRESHOLD;
        mandatoryPasswordChangeDuration = DEFAULT_MANDATORY_PASSWORD_CHANGE_DURATION;
        forcePasswordChangeOnFirstLogin = DEFAULT_FORCE_PASSWORD_CHANGE_ON_FIRST_LOGIN;
        passwordComplexityRegex = DEFAULT_PASSWORD_COMPLEXITY_REGEX;
        minimumPasswordStrength = DEFAULT_MINIMUM_PASSWORD_STRENGTH;
        minimumPasswordLength = DEFAULT_MINIMUM_PASSWORD_LENGTH;
        passwordPolicyMessage = buildDefaultPolicyMessage(minimumPasswordLength);
    }

    @JsonCreator
    public PasswordPolicyConfig(
            @JsonProperty("allowPasswordResets") final Boolean allowPasswordResets,
            @JsonProperty("neverUsedAccountDeactivationThreshold") final
            StroomDuration neverUsedAccountDeactivationThreshold,
            @JsonProperty("unusedAccountDeactivationThreshold") final StroomDuration unusedAccountDeactivationThreshold,
            @JsonProperty("mandatoryPasswordChangeDuration") final StroomDuration mandatoryPasswordChangeDuration,
            @JsonProperty("forcePasswordChangeOnFirstLogin") final Boolean forcePasswordChangeOnFirstLogin,
            @JsonProperty("passwordComplexityRegex") final String passwordComplexityRegex,
            @JsonProperty("minimumPasswordStrength") final Integer minimumPasswordStrength,
            @JsonProperty("minimumPasswordLength") final Integer minimumPasswordLength,
            @JsonProperty("passwordPolicyMessage") final String passwordPolicyMessage) {

        this.allowPasswordResets = Objects.requireNonNullElse(
                allowPasswordResets,
                DEFAULT_ALLOW_PASSWORD_RESETS);
        this.neverUsedAccountDeactivationThreshold = Objects.requireNonNullElse(
                neverUsedAccountDeactivationThreshold,
                DEFAULT_NEVER_USED_ACCOUNT_DEACTIVATION_THRESHOLD);
        this.unusedAccountDeactivationThreshold = Objects.requireNonNullElse(
                unusedAccountDeactivationThreshold,
                DEFAULT_UNUSED_ACCOUNT_DEACTIVATION_THRESHOLD);
        this.mandatoryPasswordChangeDuration = Objects.requireNonNullElse(
                mandatoryPasswordChangeDuration,
                DEFAULT_MANDATORY_PASSWORD_CHANGE_DURATION);
        this.forcePasswordChangeOnFirstLogin = Objects.requireNonNullElse(
                forcePasswordChangeOnFirstLogin,
                DEFAULT_FORCE_PASSWORD_CHANGE_ON_FIRST_LOGIN);
        this.passwordComplexityRegex = Objects.requireNonNullElse(
                passwordComplexityRegex,
                DEFAULT_PASSWORD_COMPLEXITY_REGEX);
        this.minimumPasswordStrength = Objects.requireNonNullElse(
                minimumPasswordStrength,
                DEFAULT_MINIMUM_PASSWORD_STRENGTH);
        this.minimumPasswordLength = Objects.requireNonNullElse(
                minimumPasswordLength,
                DEFAULT_MINIMUM_PASSWORD_LENGTH);
        this.passwordPolicyMessage = Objects.requireNonNullElseGet(
                passwordPolicyMessage,
                () -> buildDefaultPolicyMessage(this.minimumPasswordLength));
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }

    public StroomDuration getNeverUsedAccountDeactivationThreshold() {
        return neverUsedAccountDeactivationThreshold;
    }

    public StroomDuration getUnusedAccountDeactivationThreshold() {
        return unusedAccountDeactivationThreshold;
    }

    public StroomDuration getMandatoryPasswordChangeDuration() {
        return mandatoryPasswordChangeDuration;
    }

    public boolean isForcePasswordChangeOnFirstLogin() {
        return forcePasswordChangeOnFirstLogin;
    }

    public String getPasswordComplexityRegex() {
        return passwordComplexityRegex;
    }

    public Integer getMinimumPasswordStrength() {
        return minimumPasswordStrength;
    }

    public int getMinimumPasswordLength() {
        return minimumPasswordLength;
    }

    public String getPasswordPolicyMessage() {
        return passwordPolicyMessage;
    }

    private static String buildDefaultPolicyMessage(final int minimumPasswordLength) {
        return "To conform with our Strong Password policy, " +
               "you are required to use" +
               " a sufficiently strong password. Password must be more than " +
               minimumPasswordLength + " characters.";
    }

    @Override
    public String toString() {
        return "PasswordPolicyConfig{" +
               "allowPasswordResets=" + allowPasswordResets +
               ", neverUsedAccountDeactivationThreshold=" + neverUsedAccountDeactivationThreshold +
               ", unusedAccountDeactivationThreshold=" + unusedAccountDeactivationThreshold +
               ", mandatoryPasswordChangeDuration=" + mandatoryPasswordChangeDuration +
               ", forcePasswordChangeOnFirstLogin=" + forcePasswordChangeOnFirstLogin +
               ", passwordComplexityRegex='" + passwordComplexityRegex + '\'' +
               ", minimumPasswordStrength=" + minimumPasswordStrength +
               ", minimumPasswordLength=" + minimumPasswordLength +
               ", passwordPolicyMessage='" + passwordPolicyMessage + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final PasswordPolicyConfig that = (PasswordPolicyConfig) object;
        return allowPasswordResets == that.allowPasswordResets
               && forcePasswordChangeOnFirstLogin == that.forcePasswordChangeOnFirstLogin
               && Objects.equals(neverUsedAccountDeactivationThreshold, that.neverUsedAccountDeactivationThreshold)
               && Objects.equals(unusedAccountDeactivationThreshold, that.unusedAccountDeactivationThreshold)
               && Objects.equals(mandatoryPasswordChangeDuration, that.mandatoryPasswordChangeDuration)
               && Objects.equals(passwordComplexityRegex, that.passwordComplexityRegex)
               && Objects.equals(minimumPasswordStrength, that.minimumPasswordStrength)
               && Objects.equals(minimumPasswordLength, that.minimumPasswordLength)
               && Objects.equals(passwordPolicyMessage, that.passwordPolicyMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowPasswordResets,
                neverUsedAccountDeactivationThreshold,
                unusedAccountDeactivationThreshold,
                mandatoryPasswordChangeDuration,
                forcePasswordChangeOnFirstLogin,
                passwordComplexityRegex,
                minimumPasswordStrength,
                minimumPasswordLength,
                passwordPolicyMessage);
    }
}
