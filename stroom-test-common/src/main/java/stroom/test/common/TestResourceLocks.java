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

package stroom.test.common;

/**
 * Static strings for use as {@link org.junit.jupiter.api.parallel.ResourceLock} keys
 * in tests. All string values must be unique.
 */
public class TestResourceLocks {

    private TestResourceLocks() {
    }

    /**
     * Resource lock on network port 8080 (Stroom's default app port).
     */
    public static final String STROOM_APP_PORT_8080 = "STROOM_APP_PORT_8080";

    /**
     * Resource lock on network port 8090 (Stroom-Proxy's default app port).
     */
    public static final String STROOM_PROXY_APP_PORT_8090 = "STROOM_PROXY_APP_PORT_8090";

}
