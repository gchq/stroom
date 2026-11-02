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

/// A pipeline Stroom can't load (e.g. a property its element type no longer has, or an element type this Stroom
/// doesn't have), or that the user may not read. Its message is Stroom's own reason. The tools that meet one say
/// so for that pipeline and go on with the others.
class PipelineUnreadableException extends RuntimeException {

    /// @param reason Stroom's reason.
    PipelineUnreadableException(final String reason) {
        super(reason);
    }
}
