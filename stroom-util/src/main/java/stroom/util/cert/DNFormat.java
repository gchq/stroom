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

package stroom.util.cert;

/**
 * The format of the Distinguished Name as used in LDAP or X509 certificates.
 */
public enum DNFormat {
    /**
     * Legacy OpenSSL '/' delimited DN format.
     */
    OPEN_SSL("/"),
    /**
     * LDAP ',' delimited DN format.
     */
    LDAP(","),
    ;

    private final String delimiter;

    DNFormat(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDelimiter() {
        return this.delimiter;
    }
}
