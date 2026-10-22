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

import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SampleRefusal;

import java.util.Map;

/// Profiles sample text: its format, fields, timestamp patterns, what differs between files, which parser reads it
/// and whether a text converter is needed (the Stroom MCP server's `utils/profile.py` `profile` and
/// `profile_many`, and `tools/translation.py` `with_fragment_setup`).
///
/// The plan ([BuildPlan]) and start_onboarding read samples through it. It is bound to [Profiled], the ported
/// profiler ([stroom.ai.impl.tools.sample.SampleProfiler]); [Unavailable] is for tests of a plan without it.
public interface SampleProfiling {

    /// @param sample One sample's text.
    /// @return Its profile, as the Stroom MCP server's `profile` gave it: at least `format` (e.g. `json lines`,
    /// `key=value`, `xml fragments`), `suggested_parser`, and where they apply `malformed_lines`, `namespace`,
    /// `root` and `record_element`.
    /// @throws ToolRefusal if it can't be profiled, saying why.
    Map<String, Object> profile(String sample);

    /// @param samples Samples' text, by name.
    /// @return Their profile together, as the Stroom MCP server's `profile_many` gave it (with `files`, each
    /// file's own profile).
    /// @throws ToolRefusal if they can't be profiled, saying why.
    Map<String, Object> profileMany(Map<String, String> samples);

    /// @param profiled A profile.
    /// @return It, for XML fragments with the wrapper this environment uses for them; any other profile as it is.
    default Map<String, Object> withFragmentSetup(final Map<String, Object> profiled) {
        return profiled;
    }


    // --------------------------------------------------------------------------------


    /// Profiles with the ported profiler ([stroom.ai.impl.tools.sample.SampleProfiler]), as the Stroom MCP server
    /// profiled samples: up to [stroom.ai.impl.tools.sample.SampleProfiler#MAX_RECORDS] records each.
    final class Profiled implements SampleProfiling {

        @Override
        public Map<String, Object> profile(final String sample) {
            try {
                return SampleProfiler.profile(sample);
            } catch (final SampleRefusal e) {
                throw new ToolRefusal(e.getMessage());
            }
        }

        @Override
        public Map<String, Object> profileMany(final Map<String, String> samples) {
            try {
                return SampleProfiler.profileMany(samples, SampleProfiler.MAX_RECORDS);
            } catch (final SampleRefusal e) {
                throw new ToolRefusal(e.getMessage());
            }
        }
    }

    /// Profiles nothing, and says so: for a plan without sample profiling (e.g. in tests).
    final class Unavailable implements SampleProfiling {

        /// What it says.
        static final String NOT_AVAILABLE = "Profiling sample text isn't available in this Stroom yet.";

        @Override
        public Map<String, Object> profile(final String sample) {
            throw new ToolRefusal(NOT_AVAILABLE);
        }

        @Override
        public Map<String, Object> profileMany(final Map<String, String> samples) {
            throw new ToolRefusal(NOT_AVAILABLE);
        }
    }
}
