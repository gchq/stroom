package stroom.security.shared.changepassword;

import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class InternalIdpPasswordPolicyConfig {

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

    @JsonCreator
    public InternalIdpPasswordPolicyConfig(
            @JsonProperty("passwordComplexityRegex") final String passwordComplexityRegex,
            @JsonProperty("minimumPasswordStrength") final Integer minimumPasswordStrength,
            @JsonProperty("minimumPasswordLength") final Integer minimumPasswordLength,
            @JsonProperty("passwordPolicyMessage") final String passwordPolicyMessage) {

        this.passwordComplexityRegex = passwordComplexityRegex;
        this.minimumPasswordStrength = minimumPasswordStrength;
        this.minimumPasswordLength = minimumPasswordLength;
        this.passwordPolicyMessage = passwordPolicyMessage;
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
}
