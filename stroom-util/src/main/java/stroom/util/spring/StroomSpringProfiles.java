/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.spring;

public final class StroomSpringProfiles {
    /**
     * Beans that should act in production only should have this profile.
     */
    public static final String PROD = "production";

    /**
     * Beans that should exist only in integration tests should have this
     * profile.
     *
     * Mostly TEST and PROD are appropriate profiles for the contexts, but in
     * one or two cases they are insufficient.
     */
    public static final String IT = "misc";

    /**
     * This is used wherever stroomCoreServerLocalTestingContext.xml is used.
     */
    public static final String TEST = "test";

    private StroomSpringProfiles() {
    }
}
