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

import java.util.List;

/// Told about the commits of an index shard writer, so that the streams whose documents a commit makes
/// safe can be recorded, and the streams that a failed commit has put in doubt can be recovered.
public interface IndexShardCommitListener {

    /// Called before a commit begins.
    ///
    /// @return The ids of the pending stream rows that the commit will make safe, to be passed to
    /// [#afterCommit(List)] if it succeeds.
    List<Long> beforeCommit();

    /// Called after a commit has succeeded.
    ///
    /// @param rowIds What [#beforeCommit()] returned before the commit began.
    void afterCommit(List<Long> rowIds);

    /// Called after a commit has failed, when it is not known which documents reached the disk.
    void commitFailed();
}
