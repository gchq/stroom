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

package stroom.proxy.app.handler;

import stroom.util.shared.NullSafe;

import java.nio.file.Path;

public interface ForwardDestination {

    /**
     * Add sourceDir to this {@link ForwardDestination}.
     * If successful, sourceDir will be moved/deleted so should not be used by the caller
     * after calling this method.
     */
    void add(Path sourceDir);

    /**
     * @return The name of the destination
     */
    String getName();

    DestinationType getDestinationType();

    /**
     * @return Any details of the destination, e.g. url, path, etc.
     */
    String getDestinationDescription();

    /**
     * @return True if this destination is configured with a check for its liveness.
     */
    default boolean hasLivenessCheck() {
        return false;
    }

    /**
     * @return True if the liveness check indicates that the destination is live and ready
     * to have data forwarded to it. If the check fails, an exception will be thrown and the
     * message will provide details of why the liveness check is failing.
     * If hasLivenessCheck() returns false, performLivenessCheck() will always return true.
     */
    default boolean performLivenessCheck() throws Exception {
        return true;
    }

    default String asString() {
        String str = this.getClass().getSimpleName() + " " + getName();
        final String desc = getDestinationDescription();
        if (NullSafe.isNonBlankString(desc)) {
            str += " - " + desc;
        }
        return str;
    }


    // --------------------------------------------------------------------------------


    enum DestinationType {
        /**
         * Forwards to a filesystem.
         */
        FILE,
        /**
         * Forwards to HTTP POST endpoint.
         */
        HTTP,
        /**
         * Forwards to multiple destinations.
         */
        MULTI,
        ;
    }
}
