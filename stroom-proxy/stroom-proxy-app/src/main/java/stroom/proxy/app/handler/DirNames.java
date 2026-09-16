/*
 * Copyright 2024 Crown Copyright
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

public final class DirNames {

    private DirNames() {
        // Constants.
    }

    /**
     * Receipt's node-local scratch: the spool a zip body is written to so that it can be read through
     * its central directory, or with an instant file forwarder the directory a group is built in
     * before the destination takes it. Cleared at start-up.
     */
    public static final String RECEIVING = "01_receiving";






    /**
     * Where we perform forwarding.
     */
    public static final String FORWARDING = "50_forwarding";
}
