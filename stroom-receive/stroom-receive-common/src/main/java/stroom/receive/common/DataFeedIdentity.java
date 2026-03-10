/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.meta.api.AttributeMap;
import stroom.util.shared.string.CIKey;

import java.time.Instant;
import java.util.Map;

/**
 * An identity for using the datafeed interface.
 * Each identity is associated with a set of meta attributes
 * that will be set on data receipt.
 */
public interface DataFeedIdentity {

    /**
     * @return The meta attributes associated with this identity.
     * These trump any headers.
     */
    Map<String, String> getStreamMetaData();

    /**
     * @return The meta attributes associated with this identity.
     * These trump any headers.
     */
    Map<CIKey, String> getCIStreamMetaData();

    /**
     * @return The meta attributes associated with this identity.
     * These trump any headers.
     */
    default AttributeMap getAttributeMap() {
        return new AttributeMap(getStreamMetaData());
    }

    /**
     * @return The expiry date of this identity as millis since the epoch.
     */
    long getExpiryDateEpochMs();

    /**
     * @return The expiry date of this identity.
     */
    default Instant getExpiryDate() {
        return Instant.ofEpochMilli(getExpiryDateEpochMs());
    }
}
