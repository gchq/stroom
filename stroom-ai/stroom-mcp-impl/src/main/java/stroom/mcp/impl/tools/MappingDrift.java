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

import java.util.List;
import java.util.Optional;

/// Whether a build's XSLTs are still what their kept mappings generate, for the checks before promotion
/// ([BuildChecks]): the Stroom MCP server's `tools/builds.py` `_drift`, `_plan_drift` and `lost_mappings`, which
/// regenerate an XSLT from its mapping (`utils/xsltgen.py`) and compare.
///
/// It is bound to [GeneratedDrift], on the ported generators; [#NONE] finds no drift (for tests of the checks
/// without them).
interface MappingDrift {

    /// Finds no drift.
    MappingDrift NONE = new MappingDrift() {
        @Override
        public Optional<String> drift(final KeptMappings.KeptXslt kept) {
            return Optional.empty();
        }

        @Override
        public Optional<String> planDrift(final KeptMappings.KeptXslt kept) {
            return Optional.empty();
        }

        @Override
        public List<String> lostMappings(final String pipelineUuid) {
            return List.of();
        }
    };

    /// @param kept A translation mapping kept with its XSLT.
    /// @return Why the XSLT is no longer what its mapping generates (edited by hand, an earlier generator, or a
    /// mapping that no longer generates), or empty if it is.
    Optional<String> drift(KeptMappings.KeptXslt kept);

    /// @param kept An index or CEF plan kept with its XSLT.
    /// @return Why the XSLT was edited by hand since it was saved from its plan, or empty if it wasn't.
    Optional<String> planDrift(KeptMappings.KeptXslt kept);

    /// @param pipelineUuid A pipeline whose own XSLTs keep no mapping.
    /// @return For each of them saved with a mapping that its Documentation tab no longer holds: why.
    List<String> lostMappings(String pipelineUuid);
}
