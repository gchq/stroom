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

package stroom.index.impl;

/// Told of each commit a writer makes while it is open, so that what the commit makes safe can be recorded.
/// Not told of the commit a writer makes as it closes, which is recorded with the close.
public interface IndexShardCommitListener {

    /// Called before a commit begins, to note what it will make safe.
    ///
    /// @return What to do once the commit has succeeded. Not run if the commit fails.
    Runnable beforeCommit();
}
