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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Profiles sample text: its format, fields, timestamp patterns, what differs between files, which parser reads it and
/// whether a text converter is needed.
///
/// The plan ([BuildPlan]) and start_onboarding read samples through it. It is bound to [Profiled], the sample profiler
/// ([stroom.ai.impl.tools.sample.SampleProfiler]).
public interface SampleProfiling {

    /// @param sample One sample's text.
    /// @return Its profile: at least `format` (e.g. `json lines`, `key=value`, `xml fragments`), `suggested_parser`,
    /// and where they apply `malformed_lines`, `namespace`, `root` and `record_element`.
    /// @throws ToolRefusal if it can't be profiled, saying why.
    Map<String, Object> profile(String sample);

    /// @param samples Samples' text, by name.
    /// @return Their profile together (with `files`, each file's own profile).
    /// @throws ToolRefusal if they can't be profiled, saying why.
    Map<String, Object> profileMany(Map<String, String> samples);

    /// @param profiled A profile.
    /// @return It, for XML fragments with the wrapper this environment uses for them; any other profile as it is.
    default Map<String, Object> withFragmentSetup(final Map<String, Object> profiled) {
        return withFragmentSetup(profiled, null);
    }

    /// A profile of XML fragments, with the wrapper this environment uses: its own (event-logging fragments: an
    /// event-logging:3 one, else the most used), else the standard one (an `<Events>` one for event-logging
    /// fragments, in the version the user chose for the build, else the newest this Stroom holds).
    ///
    /// @param profiled      A profile.
    /// @param chosenVersion The event-logging version the user chose for the build, or null if none was chosen.
    /// @return It, for XML fragments with the wrapper; any other profile as it is.
    default Map<String, Object> withFragmentSetup(final Map<String, Object> profiled, final String chosenVersion) {
        return withFragmentSetup(profiled, chosenVersion, null);
    }

    /// As [#withFragmentSetup(Map, String)], for a build that writes an environment's fork of the event-logging
    /// schema: Event fragments that declare no namespace are wrapped in the fork's, and the environment's own
    /// wrappers in it are preferred.
    ///
    /// @param profiled      A profile.
    /// @param chosenVersion The version chosen for the build (the fork's, for a fork), or null.
    /// @param fork          The fork chosen for the build, or null for event-logging.
    /// @return It, for XML fragments with the wrapper; any other profile as it is.
    default Map<String, Object> withFragmentSetup(final Map<String, Object> profiled,
                                                  final String chosenVersion,
                                                  final XmlSchemaDoc fork) {
        return profiled;
    }

    /// Profiles samples as start_onboarding and profile_sample do: one on its own, several together, and XML
    /// fragments with the environment's own wrapper ([#withFragmentSetup(Map)]).
    ///
    /// @param named Samples' text, by name: at least one.
    /// @return Their profile.
    /// @throws ToolRefusal if they can't be profiled, saying why.
    default Map<String, Object> profileAll(final Map<String, String> named) {
        return withFragmentSetup(named.size() > 1
                ? profileMany(named)
                : profile(named.values().iterator().next()));
    }

    /// The setup of [#withFragmentSetup(Map, String)], given what the environment holds.
    ///
    /// @param profiled A profile.
    /// @param found    The environment's fragment wrappers outside the workspace ([FragmentWrappers#find()]).
    /// @param version  The event-logging version an `<Events>` wrapper is written in, or null for none.
    /// @return The profile, for XML fragments with their wrapper, parser and XSLT input; any other as it is.
    static Map<String, Object> fragmentSetup(final Map<String, Object> profiled,
                                             final List<Map<String, Object>> found,
                                             final String version) {
        return fragmentSetup(profiled, found, version, null);
    }

    /// As [#fragmentSetup(Map, List, String)], for a build that writes an environment's fork.
    ///
    /// @param profiled A profile.
    /// @param found    The environment's fragment wrappers outside the workspace.
    /// @param version  The version an `<Events>` wrapper is written with, or null for none.
    /// @param fork     The fork the build writes, or null for event-logging.
    /// @return The profile, for XML fragments with their wrapper, parser and XSLT input; any other as it is.
    static Map<String, Object> fragmentSetup(final Map<String, Object> profiled,
                                             final List<Map<String, Object>> found,
                                             final String version,
                                             final XmlSchemaDoc fork) {
        // Several files: as the first is read.
        final Map<?, ?> files = profiled.get("files") instanceof final Map<?, ?> map
                ? map
                : Map.of();
        final Map<?, ?> first = files.values().stream()
                .filter(Map.class::isInstance)
                .<Map<?, ?>>map(value -> (Map<?, ?>) value)
                .findFirst()
                .orElse(profiled);
        final Object record = JsonValues.truthy(profiled.get("record_element"))
                ? profiled.get("record_element")
                : first.get("record_element");
        if (!SampleProfiler.XML_FRAGMENTS.equals(profiled.get("format")) || !JsonValues.truthy(record)) {
            return profiled;
        }
        final boolean events = JsonValues.truthy(first.get("event_logging"));
        // Event fragments: a wrapper in their own namespace, else the build's (an environment's fork's, or
        // event-logging's).
        final String buildNamespace = fork == null
                ? "event-logging:3"
                : NullSafe.string(fork.getNamespaceURI()).strip();
        final String eventsNamespace = first.get("namespace") instanceof final String declared && !declared.isBlank()
                ? declared
                : buildNamespace;
        final List<Map<String, Object>> fitting = events
                ? found.stream().filter(wrapper -> eventsNamespace.equals(wrapper.get("namespace"))).toList()
                : found;
        final Map<String, Object> wrapper;
        if (!fitting.isEmpty()) {
            wrapper = mostUsed(fitting);
        } else if (events && version != null) {
            wrapper = new LinkedHashMap<>();
            wrapper.put("code", fork == null
                    ? SampleProfiler.EVENTS_FRAGMENT_WRAPPER.replace("{version}", version)
                    : SampleProfiler.eventsFragmentWrapper(buildNamespace, fork.getSystemId().strip(), version));
        } else {
            wrapper = null;
        }
        final List<Map<String, Object>> others = new ArrayList<>();
        found.stream().filter(other -> other != wrapper).forEach(others::add);
        final Object namespace = first.get("namespace");
        final Map<String, Object> out = new LinkedHashMap<>(profiled);
        out.putAll(SampleProfiler.xmlFragmentSetup(namespace == null
                ? null
                : String.valueOf(namespace), String.valueOf(record), wrapper, others));
        return out;
    }

    // The first wrapper of the root and namespace most of them have.
    private static Map<String, Object> mostUsed(final List<Map<String, Object>> wrappers) {
        final Map<List<Object>, Integer> counts = new LinkedHashMap<>();
        wrappers.forEach(wrapper -> counts.merge(key(wrapper), 1, Integer::sum));
        List<Object> most = null;
        int best = 0;
        for (final Map.Entry<List<Object>, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                most = entry.getKey();
                best = entry.getValue();
            }
        }
        final List<Object> wanted = most;
        return wrappers.stream().filter(wrapper -> key(wrapper).equals(wanted)).findFirst().orElseThrow();
    }

    private static List<Object> key(final Map<String, Object> wrapper) {
        final List<Object> key = new ArrayList<>();
        key.add(wrapper.get("root"));
        key.add(wrapper.get("namespace"));
        return key;
    }


    // --------------------------------------------------------------------------------


    /// Profiles with the sample profiler ([stroom.ai.impl.tools.sample.SampleProfiler]): up to
    /// [stroom.ai.impl.tools.sample.SampleProfiler#MAX_RECORDS] records each. XML fragments are given the environment's
    /// own wrapper ([FragmentWrappers]).
    final class Profiled implements SampleProfiling {

        private final FragmentWrappers wrappers;

        /// Profiles with no environment to read wrappers from: XML fragments get the standard wrapper (the
        /// records:2 one), as in an environment that has none and no event-logging schema.
        public Profiled() {
            this.wrappers = null;
        }

        /// @param wrappers The environment's fragment wrappers and event-logging versions.
        @Inject
        public Profiled(final FragmentWrappers wrappers) {
            this.wrappers = Objects.requireNonNull(wrappers);
        }

        @Override
        public Map<String, Object> withFragmentSetup(final Map<String, Object> profiled,
                                                     final String chosenVersion,
                                                     final XmlSchemaDoc fork) {
            if (!SampleProfiler.XML_FRAGMENTS.equals(profiled.get("format"))) {
                // Nothing to look up.
                return profiled;
            }
            if (wrappers == null) {
                return fragmentSetup(profiled, List.of(), null);
            }
            // A fork's version is the fork's own, not one of event-logging's.
            return fragmentSetup(profiled, wrappers.find(), fork == null
                    ? wrappers.version(chosenVersion)
                    : chosenVersion, fork);
        }

        @Override
        public Map<String, Object> profile(final String sample) {
            return SampleProfiler.profile(sample);
        }

        @Override
        public Map<String, Object> profileMany(final Map<String, String> samples) {
            return SampleProfiler.profileMany(samples, SampleProfiler.MAX_RECORDS);
        }
    }
}
