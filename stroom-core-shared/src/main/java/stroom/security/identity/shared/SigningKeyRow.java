/*
 * Copyright 2026 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * One signing key, as much as anyone outside the server is told about it.
 * <p>
 * <b>Nothing here describes the key itself.</b> The stored key is written with its private material
 * included, so it must never be read into anything that leaves the server - not a response, not a log, not
 * an audit event. This type exists rather than reusing anything that holds a key precisely so that there is
 * no path by which key material could be added to it by accident.
 * </p>
 * <p>
 * The identifier is the database row's surrogate key. It is not displayed and means nothing outside the
 * database; it exists so that revoking a key has something to name. The key's {@code kid} is deliberately
 * absent even though it is published openly in the JWKS document, because nothing here needs it.
 * </p>
 */
@JsonInclude(Include.NON_NULL)
public class SigningKeyRow {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final SigningKeyStatus status;
    @JsonProperty
    private final Long issuedMs;
    @JsonProperty
    private final Long expiresMs;

    @JsonCreator
    public SigningKeyRow(@JsonProperty("id") final int id,
                         @JsonProperty("status") final SigningKeyStatus status,
                         @JsonProperty("issuedMs") final Long issuedMs,
                         @JsonProperty("expiresMs") final Long expiresMs) {
        this.id = id;
        this.status = status;
        this.issuedMs = issuedMs;
        this.expiresMs = expiresMs;
    }

    public int getId() {
        return id;
    }

    public SigningKeyStatus getStatus() {
        return status;
    }

    public Long getIssuedMs() {
        return issuedMs;
    }

    /**
     * When the key stops being trusted, or null where no date applies - the active key has no expiry
     * stamped because it stops being published when rotation retires it, and a revoked key is not trusted
     * whatever its expiry said.
     */
    public Long getExpiresMs() {
        return expiresMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SigningKeyRow that = (SigningKeyRow) o;
        return id == that.id
               && status == that.status
               && Objects.equals(issuedMs, that.issuedMs)
               && Objects.equals(expiresMs, that.expiresMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, issuedMs, expiresMs);
    }

    @Override
    public String toString() {
        return "SigningKeyRow{" +
               "id=" + id +
               ", status=" + status +
               ", issuedMs=" + issuedMs +
               ", expiresMs=" + expiresMs +
               '}';
    }
}
