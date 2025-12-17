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

package stroom.credentials.shared;

/**
 * The type of credentials being stored.
 */
public enum CredentialsType {
    USERNAME_PASSWORD("Username / Password"),
    ACCESS_TOKEN("Access Token"),
    PRIVATE_CERT("Private Key");

    /** What to display in drop-down lists */
    private final String displayName;

    /** Private constructor */
    CredentialsType(final String displayName) {
        this.displayName = displayName;
    }

    /** Returns the name to display in drop-down lists */
    public String getDisplayName() {
        return displayName;
    }
}
