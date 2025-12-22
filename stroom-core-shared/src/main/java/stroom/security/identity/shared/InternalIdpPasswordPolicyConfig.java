/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.shared;

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

    @JsonProperty
    @JsonPropertyDescription("Will the UI allow password resets")
    private boolean allowPasswordResets;

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
    private final int minimumPasswordStrength;

    @Min(0)
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The minimum number of characters that new passwords need to contain.")
    private final int minimumPasswordLength;

    @JsonProperty
    @JsonPropertyDescription("A message informing users of the password policy")
    private final String passwordPolicyMessage;

    @JsonCreator
    public InternalIdpPasswordPolicyConfig(
            @JsonProperty("allowPasswordResets") final boolean allowPasswordResets,
            @JsonProperty("passwordComplexityRegex") final String passwordComplexityRegex,
            @JsonProperty("minimumPasswordStrength") final int minimumPasswordStrength,
            @JsonProperty("minimumPasswordLength") final int minimumPasswordLength,
            @JsonProperty("passwordPolicyMessage") final String passwordPolicyMessage) {

        this.allowPasswordResets = allowPasswordResets;
        this.passwordComplexityRegex = passwordComplexityRegex;
        this.minimumPasswordStrength = minimumPasswordStrength;
        this.minimumPasswordLength = minimumPasswordLength;
        this.passwordPolicyMessage = passwordPolicyMessage;
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }

    public String getPasswordComplexityRegex() {
        return passwordComplexityRegex;
    }

    public int getMinimumPasswordStrength() {
        return minimumPasswordStrength;
    }

    public int getMinimumPasswordLength() {
        return minimumPasswordLength;
    }

    public String getPasswordPolicyMessage() {
        return passwordPolicyMessage;
    }
}
