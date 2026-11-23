/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Class to wrap Credentials with enough permission info
 * to help the UI.
 */
@JsonPropertyOrder({
        "credential",
        "edit",
        "delete",
        "certificateExpiryTimeMs",
        "expiryWarningPeriodMs"
})
@JsonInclude(Include.NON_NULL)
public class CredentialWithPerms {

    @JsonProperty
    private final Credential credential;

    /**
     * Edit permission
     */
    @JsonProperty
    private final boolean edit;

    /**
     * Delete permission
     */
    @JsonProperty
    private final boolean delete;

    /// When the certificates in the credential's key store expire, if it has one.
    @JsonProperty
    private final Long certificateExpiryTimeMs;

    /// How long before a credential expires that it is shown as expiring.
    @JsonProperty
    private final Long expiryWarningPeriodMs;

    /// Constructor, with no certificate expiry or warning period.
    ///
    /// @param credential Must not be null.
    /// @param edit       If the user has edit permission.
    /// @param delete     If the user has delete permission.
    public CredentialWithPerms(final Credential credential,
                               final Boolean edit,
                               final Boolean delete) {
        this(credential, edit, delete, null, null);
    }

    /// Constructor.
    ///
    /// @param credential              Must not be null.
    /// @param edit                    If the user has edit permission.
    /// @param delete                  If the user has delete permission.
    /// @param certificateExpiryTimeMs When the certificates in the credential's key store expire,
    ///                                in ms since the epoch, or null if it has none.
    /// @param expiryWarningPeriodMs   How long before it expires that a credential is shown as
    ///                                expiring, in ms, or null for the client's default.
    @JsonCreator
    public CredentialWithPerms(
            @JsonProperty("credential") final Credential credential,
            @JsonProperty("edit") final Boolean edit,
            @JsonProperty("delete") final Boolean delete,
            @JsonProperty("certificateExpiryTimeMs") final Long certificateExpiryTimeMs,
            @JsonProperty("expiryWarningPeriodMs") final Long expiryWarningPeriodMs) {
        this.credential = credential;
        this.edit = Objects.requireNonNullElse(edit, false);
        this.delete = Objects.requireNonNullElse(delete, false);
        this.certificateExpiryTimeMs = certificateExpiryTimeMs;
        this.expiryWarningPeriodMs = expiryWarningPeriodMs;
    }

    /**
     * @return Never returns null.
     */
    public Credential getCredential() {
        return credential;
    }

    public boolean isEdit() {
        return edit;
    }

    public boolean isDelete() {
        return delete;
    }

    /// @return When the certificates in the credential's key store expire, in ms since the epoch,
    ///         or null if it has none.
    public Long getCertificateExpiryTimeMs() {
        return certificateExpiryTimeMs;
    }

    /// @return How long before it expires that a credential is shown as expiring, in ms, or null
    ///         for the client's default.
    public Long getExpiryWarningPeriodMs() {
        return expiryWarningPeriodMs;
    }

    /// @return When the credential expires, the earlier of its expiry date and when its
    ///         certificates expire, in ms since the epoch, or null if it never does.
    @JsonIgnore
    public Long getEffectiveExpiryTimeMs() {
        return isCertificateExpiryFirst()
                ? certificateExpiryTimeMs
                : getExpiryDateMs();
    }

    /// @return True if the certificates in the credential's key store expire before its expiry
    ///         date, or it has no expiry date.
    @JsonIgnore
    public boolean isCertificateExpiryFirst() {
        final Long expiryDateMs = getExpiryDateMs();
        return certificateExpiryTimeMs != null
               && (expiryDateMs == null || certificateExpiryTimeMs < expiryDateMs);
    }

    private Long getExpiryDateMs() {
        return credential == null
                ? null
                : credential.getExpiryTimeMs();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialWithPerms that = (CredentialWithPerms) o;
        return Objects.equals(credential, that.credential);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(credential);
    }

    @Override
    public String toString() {
        return "CredentialsWithPerms{" +
               "credentials=" + credential +
               ", edit=" + edit +
               ", delete=" + delete +
               ", certificateExpiryTimeMs=" + certificateExpiryTimeMs +
               ", expiryWarningPeriodMs=" + expiryWarningPeriodMs +
               '}';
    }
}
