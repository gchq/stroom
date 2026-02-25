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

package stroom.planb.impl;

import java.util.regex.Pattern;

public class PlanBNameValidator {

    // Deliberately forces lower case naming.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_0-9]+$");

    private PlanBNameValidator() {
        // Utility class.
    }

    public static boolean isValidName(final String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public static String getPattern() {
        return NAME_PATTERN.pattern();
    }
}
