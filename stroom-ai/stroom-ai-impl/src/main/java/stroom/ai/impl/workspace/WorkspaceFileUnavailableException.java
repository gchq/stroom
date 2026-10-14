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

package stroom.ai.impl.workspace;

/// Thrown when a workspace file's content is no longer held: it reached its retention age, or the user no longer
/// has access to the data it came from.
public class WorkspaceFileUnavailableException extends RuntimeException {

    private final int fileId;

    /// @param fileId The file.
    /// @param path   The file's path, or null.
    /// @param reason Why it is unavailable.
    public WorkspaceFileUnavailableException(final int fileId, final String path, final String reason) {
        super("The workspace file " + (path != null
                ? path
                : String.valueOf(fileId)) + " is no longer available: " + reason
              + ". Re-run the query that made it, or ask the user to attach the data again.");
        this.fileId = fileId;
    }

    public int getFileId() {
        return fileId;
    }
}
