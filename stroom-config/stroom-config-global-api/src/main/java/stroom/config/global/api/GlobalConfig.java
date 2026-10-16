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

package stroom.config.global.api;

import stroom.docref.DocRef;
import stroom.util.shared.AbstractConfig;

public interface GlobalConfig {

    /**
     * Set a doc ref property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param docRef       The new value.
     */
    void setDocRef(final AbstractConfig config,
                   final String propertyName,
                   final DocRef docRef);

    /**
     * Set an integer property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param i            The new value.
     */
    void setInt(final AbstractConfig config,
                final String propertyName,
                final int i);

    /**
     * Set a string property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param string       The new value.
     */
    void setString(final AbstractConfig config,
                   final String propertyName,
                   final String string);

    /**
     * Store a whole config.
     *
     * @param config The config object to update.
     */
    void update(AbstractConfig config);
}
