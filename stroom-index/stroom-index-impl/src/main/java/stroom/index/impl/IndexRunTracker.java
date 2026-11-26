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

/// Keeps the record of which shards a run of a processor task has written to, so that the task is only
/// completed once they have all committed. Told of every document the run adds.
public interface IndexRunTracker {

    /// For a pipeline that is not run by a processor task, which records nothing.
    IndexRunTracker NONE = new IndexRunTracker() {
        @Override
        public void beforeAdd(final long indexShardId, final String writerId) {
            // Nothing is recorded.
        }

        @Override
        public void added(final long indexShardId, final String writerId) {
            // Nothing is recorded.
        }

        @Override
        public void addFailed(final long indexShardId, final String writerId) {
            // Nothing is recorded.
        }

        @Override
        public boolean hasWritten() {
            return false;
        }
    };

    /// Called before a document is given to a writer. The first time for a shard and writer, the row that
    /// says the run has written to it is saved, so that the shard never holds documents of the run that the
    /// record does not know about.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer the document is about to be given to.
    /// @throws RunStoppedException If the run must add nothing more.
    void beforeAdd(long indexShardId, String writerId);

    /// Called once a document has been given to a writer.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer it was given to.
    void added(long indexShardId, String writerId);

    /// Called when a writer could not take a document, e.g. as its shard is full or has just closed. If it
    /// was given none of the run's documents, the row saved for it is put back as it was.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer.
    void addFailed(long indexShardId, String writerId);

    /// Says whether the run has given any shard a document.
    ///
    /// @return True if it has.
    boolean hasWritten();
}
