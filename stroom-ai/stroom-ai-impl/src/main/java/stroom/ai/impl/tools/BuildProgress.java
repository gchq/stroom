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


package stroom.ai.impl.tools;

import java.util.List;
import java.util.Optional;

/// What Stroom's own tools need to know of the build a session is working on, where builds live with the build
/// tools (`stroom-mcp-impl`).
///
/// It is bound by the module that has them (`stroom-mcp-impl`); without that module, no session has a build.
/// Inject it as `Optional<BuildProgress>` ([BuiltInToolsModule] declares it optional).
public interface BuildProgress {

    /// Why a new dashboard isn't to be proposed yet in this session: the build it is working on has an indexing
    /// pipeline whose indexing isn't verified, so its index may hold none (or not all) of the build's Events. The
    /// plan's indexing steps end with verify_index, which makes the build's own dashboard over the data's real time
    /// window.
    ///
    /// @param context The call.
    /// @return Why, naming the plan's next step; empty when the session has no build, its indexing is verified (or
    /// it does none), or its state can't be read.
    Optional<String> beforeDashboard(ToolCallContext context);

    /// The errors a build's recorded choices accept as benign for one of its pipelines, as the user's accepted
    /// errors are ([AcceptedErrors]): e.g. the JSON parser's Warning that it cut a long value, where the user chose
    /// to cut long values.
    ///
    /// @param pipelineUuid A pipeline.
    /// @return The entries; empty when it is in no build, the build's choices accept nothing, or they can't be read.
    default List<AcceptedErrors.Entry> acceptedErrors(final String pipelineUuid) {
        return List.of();
    }

    /// The errors a build's recorded choices explain for one of its pipelines without making them benign, each
    /// with the reason triage gives in place of its rule's: e.g. the JSON parser's Warning that it cut a long value,
    /// where the user chose to keep long values whole.
    ///
    /// @param pipelineUuid A pipeline.
    /// @return The entries; empty when it is in no build, the build's choices explain nothing, or they can't be
    /// read.
    default List<AcceptedErrors.Entry> explainedErrors(final String pipelineUuid) {
        return List.of();
    }
}
