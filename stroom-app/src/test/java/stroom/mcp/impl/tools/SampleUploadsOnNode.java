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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSampleUploadDao;

import jakarta.inject.Provider;

import java.util.function.LongSupplier;

/// The REAL [SampleUploads] as another node has it: its own instance, named as that node, sharing only the
/// database ([AiSampleUploadDao]) with the others, and with a clock of its own, so a test can make a node's
/// request later than another's.
public final class SampleUploadsOnNode {

    private SampleUploadsOnNode() {
    }

    /// @param aiConfigProvider The AI config.
    /// @param dao              The database's waiting files, shared by every node.
    /// @param node             The node's name, for its claims.
    /// @param clock            The node's time now, in milliseconds.
    /// @return The node's sample uploads.
    public static SampleUploads create(final Provider<AiConfig> aiConfigProvider,
                                       final AiSampleUploadDao dao,
                                       final String node,
                                       final LongSupplier clock) {
        return new SampleUploads(aiConfigProvider, dao, node, clock);
    }
}
