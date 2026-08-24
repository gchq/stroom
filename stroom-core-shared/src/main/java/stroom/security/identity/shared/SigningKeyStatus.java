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

/**
 * What a signing key is currently doing.
 * <p>
 * Derived on the server from the key's enabled flag and expiry rather than stored, so that what the screen
 * shows and what the token queries honour cannot drift apart.
 * </p>
 */
public enum SigningKeyStatus {

    /**
     * New tokens are being signed with this key.
     */
    ACTIVE("Active"),

    /**
     * No longer signing, but still trusted, so tokens already issued with it keep working until it expires.
     */
    RETIRED("Retired"),

    /**
     * Past its expiry and no longer trusted. Shown until the rotation job deletes the row.
     */
    EXPIRED("Expired"),

    /**
     * Withdrawn by an administrator. Anything signed with it is refused, whatever its expiry said.
     */
    REVOKED("Revoked");

    private final String displayValue;

    SigningKeyStatus(final String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }
}
