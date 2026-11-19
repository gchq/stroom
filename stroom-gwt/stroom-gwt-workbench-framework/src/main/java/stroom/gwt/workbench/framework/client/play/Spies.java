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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The story's [Spy]s, by name, so the story (via `StoryContext.fn(name)`, which calls
/// [#register(String)]) and its play function (via [Play#spy(String)], which calls
/// [#get(String)]) share them. The preview page shows one story, so names only need to be unique
/// within a story.
///
/// Each rendering of the story (each `StoryContext`) is a new generation of spies:
///
/// * the spies the story registers belong to that rendering, so a callback left over from an
///   earlier rendering (e.g. a debounce timer or a late server reply) that calls its spy after the
///   story renders again is ignored rather than counted as a call in the new rendering;
/// * the play function's spies always read the current rendering's spy of the same name, and fail
///   the step that reads them if the story didn't register a spy with that name, so a misspelt
///   name can't make e.g. `not().toHaveBeenCalled()` pass.
public final class Spies {

    // The spies registered by the current rendering
    private static final Map<String, Spy> REGISTERED = new HashMap<>();
    // The play function's spies, which read the registered spy with the same name
    private static final Map<String, Spy> PLAY_SPIES = new HashMap<>();
    private static int generation;

    private Spies() {
        // Static utility
    }

    /// Starts a new rendering of the story: spies registered by earlier renderings stop
    /// recording calls, and the play function's spies read those the new rendering registers.
    ///
    /// @return The new rendering's generation.
    public static int startRendering() {
        generation++;
        REGISTERED.clear();
        return generation;
    }

    /// @param name The spy's name, e.g. `onClick`.
    /// @return The current rendering's spy with the name, created if it hasn't been registered
    /// yet. It records calls only while its rendering is the current one.
    public static Spy register(final String name) {
        return REGISTERED.computeIfAbsent(name, spyName -> new Spy(spyName, generation));
    }

    /// Registers a spy for a rendering, unless the rendering has been replaced, e.g. a callback
    /// left over from an earlier rendering (a timer or a late server reply) asks its context for
    /// a spy after the story has rendered again.
    ///
    /// @param name               The spy's name, e.g. `onClick`.
    /// @param renderingGeneration The generation of the rendering asking, from
    ///                            [#startRendering()].
    /// @return The current rendering's spy with the name if the rendering is the current one,
    /// otherwise a detached spy that records nothing (so the play function can't see its calls).
    public static Spy register(final String name, final int renderingGeneration) {
        if (renderingGeneration != generation) {
            return new Spy(name, renderingGeneration);
        }
        return register(name);
    }

    /// @param renderingGeneration The generation of a rendering, from [#startRendering()].
    /// @return True if the rendering is the current one.
    public static boolean isCurrent(final int renderingGeneration) {
        return renderingGeneration == generation;
    }

    /// @param name The spy's name, e.g. `onClick`.
    /// @return The play function's spy with the name, which reads the calls of the spy the current
    /// rendering registered with the name. Reading it throws a [PlayException] if there is none.
    public static Spy get(final String name) {
        return PLAY_SPIES.computeIfAbsent(name, Spy::playSpy);
    }

    /// @param name The spy's name.
    /// @return The spy the current rendering registered with the name, or null.
    static Spy registered(final String name) {
        return REGISTERED.get(name);
    }

    /// @return The names of the spies the current rendering registered, sorted.
    static List<String> registeredNames() {
        final List<String> names = new ArrayList<>(REGISTERED.keySet());
        Collections.sort(names);
        return names;
    }

    /// @return The current rendering's generation.
    static int generation() {
        return generation;
    }
}
