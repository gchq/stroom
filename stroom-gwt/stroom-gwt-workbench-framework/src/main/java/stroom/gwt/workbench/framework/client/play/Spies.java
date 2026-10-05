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


package stroom.gwt.workbench.framework.client.play;

import java.util.HashMap;
import java.util.Map;

/// The story's [Spy]s, by name, so the story (via `StoryContext.fn(name)`) and its play function
/// (via [Play#spy(String)]) share them. The preview page shows one story, so names only need to
/// be unique within a story.
public final class Spies {

    private static final Map<String, Spy> SPIES = new HashMap<>();

    private Spies() {
        // Static utility
    }

    /// @param name The spy's name, e.g. `onClick`.
    /// @return The spy with the name, created if there isn't one yet.
    public static Spy get(final String name) {
        return SPIES.computeIfAbsent(name, Spy::new);
    }

    /// Forgets the calls of every spy, e.g. because the story is being rendered again. The spies
    /// themselves are kept, so references the play function holds stay valid.
    public static void clearAll() {
        SPIES.values().forEach(Spy::mockClear);
    }
}
