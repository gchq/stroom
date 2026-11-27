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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.docref.DocRef;

import java.util.Map;

/// The `## Fields` lines a user chose for a data source where the AGENTS docs that would apply to it conflict: a
/// build of several feeds whose folders' docs give a field differently asks which applies (decided by the user,
/// 2026-11-25), and the answer is used for the build's own data sources. Bound by stroom-mcp-impl, where builds
/// are; none bound, nothing is chosen. Inject it as `Optional<FieldRuleChoices>`.
public interface FieldRuleChoices {

    /// @param dataSource A data source, e.g. a build's index doc.
    /// @return The `## Fields` lines chosen for it, by field key (e.g. `user`); empty when none were.
    Map<String, FieldRule> chosenFor(DocRef dataSource);
}
