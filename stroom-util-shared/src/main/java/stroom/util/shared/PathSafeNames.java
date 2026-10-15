/*
 * Copyright 2026 Crown Copyright
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

package stroom.util.shared;

/// The rule for the names of things that are looked up by name in a REST URL's path
/// (`.../fetchByName/{name}`), e.g. data and index volume groups, node groups and processor
/// profiles. Such a name can't contain a `/`: the server refuses an encoded `/` in a path.
public final class PathSafeNames {

    /// What is wrong with a name that contains a `/`.
    public static final String CONTAINS_SLASH_MESSAGE = "A name can't contain '/'.";

    private PathSafeNames() {
        // Static utility
    }

    /// Checks a name.
    ///
    /// @param name The name, which may be null.
    /// @return What is wrong with it, or null if it can be used.
    public static String validate(final String name) {
        return name != null && name.contains("/")
                ? CONTAINS_SLASH_MESSAGE
                : null;
    }
}
