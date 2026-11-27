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

package stroom.ai.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Stores the sample files upload_sample has given commands for, waiting to be posted to `/mcp/upload`, so that any
/// node takes the post and a restart doesn't forget them.
///
/// A file is taken by one post at a time: a node claims it (a conditional update, so only one node's claim
/// succeeds), then marks it stored, or releases it so it can be posted again. A claim older than the given stale
/// time (left by a node that stopped part way) may be claimed again.
public interface AiSampleUploadDao {

    /// Stores a set of upload commands and its files, each waiting to be posted.
    ///
    /// @param set          What the commands were given for.
    /// @param files        The files' names, as the commands give them.
    /// @param createTimeMs When the commands were given.
    void create(UploadSet set, List<String> files, long createTimeMs);

    /// @param token The id in the commands' URL.
    /// @param nowMs The time now.
    /// @return The set of commands with this token, unless it has run out.
    Optional<UploadSet> find(String token, long nowMs);

    /// @param userUuid       A user.
    /// @param nowMs          The time now.
    /// @param staleBeforeMs  A claim made before this may be claimed again.
    /// @return How many of the user's sets of commands, not run out, have a file that may still be posted.
    int countWaiting(String userUuid, long nowMs, long staleBeforeMs);

    /// Claims a file of a set for a post, unless it is stored already or claimed by another post that isn't stale.
    ///
    /// @param setId         The set's id ([UploadSet#id()]).
    /// @param file          The file's name.
    /// @param node          The node claiming it.
    /// @param claimTimeMs   The time now, which identifies the claim.
    /// @param staleBeforeMs A claim made before this may be claimed again.
    /// @return True if this claim took the file.
    boolean claim(int setId, String file, String node, long claimTimeMs, long staleBeforeMs);

    /// Releases a claim, so the file can be posted again.
    ///
    /// @param setId       The set's id.
    /// @param file        The file's name.
    /// @param node        The node that claimed it.
    /// @param claimTimeMs When it claimed it.
    /// @return True if the claim was still held (and is now released).
    boolean release(int setId, String file, String node, long claimTimeMs);

    /// Marks a claimed file stored, so it is never posted again.
    ///
    /// @param setId        The set's id.
    /// @param file         The file's name.
    /// @param node         The node that claimed it.
    /// @param claimTimeMs  When it claimed it.
    /// @param streamId     The stream it was stored as, or null if it isn't visible yet.
    /// @param storedTimeMs The time now.
    /// @return True if the claim was still held (and the file is now marked stored).
    boolean markStored(int setId, String file, String node, long claimTimeMs, Long streamId, long storedTimeMs);

    /// Deletes the sets of commands that have run out, with their files.
    ///
    /// @param nowMs The time now.
    /// @return How many sets were deleted.
    int deleteExpired(long nowMs);

    /// @return The users who have sets of commands held.
    List<String> getUserUuids();

    /// Deletes all of a user's sets of commands, with their files.
    ///
    /// @param userUuid The user.
    /// @return How many sets were deleted.
    int deleteAllForUser(String userUuid);


    // --------------------------------------------------------------------------------


    /// A set of upload commands: what upload_sample gave them for.
    ///
    /// @param id           The set's id, or 0 before it is stored.
    /// @param token        The id in the commands' URL.
    /// @param userUuid     The user they were given to.
    /// @param feedName     The feed's name.
    /// @param feedUuid     Its UUID.
    /// @param streamType   The stream type to store the files as.
    /// @param headers      Their receipt headers.
    /// @param build        The feed's build, or null.
    /// @param expiryTimeMs When the commands run out.
    record UploadSet(int id,
                     String token,
                     String userUuid,
                     String feedName,
                     String feedUuid,
                     String streamType,
                     Map<String, String> headers,
                     String build,
                     long expiryTimeMs) {

    }
}
