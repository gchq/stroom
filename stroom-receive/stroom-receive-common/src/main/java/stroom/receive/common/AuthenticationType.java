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

package stroom.receive.common;

import stroom.docref.HasDisplayValue;

public enum AuthenticationType implements HasDisplayValue {
    /**
     * Authenticates using a Stroom Data Feed Key whose hashed form exists
     * in a Data Feed Identity file in the directory specified by the property
     * '.receive.dataFeedIdentitiesDir'.
     * <p>
     * The client must also provide an identity in the header whose key is
     * configured by '.receive.dataFeedOwnerMetaKey'.
     */
    DATA_FEED_KEY("Data feed key"),
    /**
     * Authenticates using an X509 certificate DN, that exists
     * in a Data Feed Identity file in the directory specified by the property
     * '.receive.dataFeedIdentitiesDir'. The DN is expected to be found in the header
     * whose key is configured by '.receive.x509CertificateDnHeader'.
     * <p>
     * The client must also provide an identity in the header whose key is
     * configured by '.receive.dataFeedOwnerMetaKey'.
     */
    CERTIFICATE_IDENTITY("Certificate identity"),
    /**
     * An OAuth token or a Stroom API Key
     */
    TOKEN("OAuth Token"),
    /**
     * Either authenticates the X509 certificate on the request
     * or authenticates the DN from a header if '.receive.x509CertificateDnHeader' is set.
     */
    CERTIFICATE("Client certificate"),
    ;

    private final String displayValue;

    AuthenticationType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public String toString() {
        return "AuthenticationType{" +
               "displayValue='" + displayValue + '\'' +
               '}';
    }
}
