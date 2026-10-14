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

import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Stores "Ask Stroom AI" agent runs (`ai_chat_run`). A run's status is only ever changed with a compare-and-set,
/// so that two things racing to change it (e.g. a cancel and the run finishing) cannot both win.
public interface AiChatRunDao {

    /// Creates a run.
    ///
    /// @param chatId        The chat the run is for.
    /// @param userUuid      The chat's user.
    /// @param status        The run's first status.
    /// @param nodeName      The node that will run it.
    /// @param sessionIdHash A hash of the id of the HTTP session that started it, or null.
    /// @return The new run.
    AiChatRun create(int chatId, String userUuid, AiChatRunStatus status, String nodeName, String sessionIdHash);

    /// Creates a run unless the chat already has an active one. The chat's row is locked while this is checked,
    /// so two requests cannot both start a run in the same chat.
    ///
    /// @param chatId        The chat the run is for.
    /// @param userUuid      The chat's user.
    /// @param status        The run's first status.
    /// @param nodeName      The node that will run it.
    /// @param sessionIdHash A hash of the id of the HTTP session that started it, or null.
    /// @return The new run, or empty if the chat already has an active run.
    Optional<AiChatRun> createIfNoneActive(int chatId,
                                           String userUuid,
                                           AiChatRunStatus status,
                                           String nodeName,
                                           String sessionIdHash);

    /// @param runId The run's id.
    /// @return The run, if it exists.
    Optional<AiChatRun> get(int runId);

    /// @param chatId The chat.
    /// @return The chat's most recent run, if it has had one.
    Optional<AiChatRun> getLatest(int chatId);

    /// @param chatId The chat.
    /// @return The chat's active run (one that has not ended), if there is one.
    Optional<AiChatRun> getActive(int chatId);

    /// Changes a run's status, but only if it is currently one of the expected statuses.
    ///
    /// @param runId        The run's id.
    /// @param expected     The statuses the run must have for the change to be made.
    /// @param newStatus    The status to change to.
    /// @param stopReason   Why the run stopped or paused, or null.
    /// @param errorMessage An error message, or null.
    /// @return True if the status was changed.
    boolean compareAndSetStatus(int runId,
                                Collection<AiChatRunStatus> expected,
                                AiChatRunStatus newStatus,
                                AiChatRunStopReason stopReason,
                                String errorMessage);

    /// Records that the runs are still being worked on, by updating their heartbeat to now. Runs that have ended
    /// are left alone.
    ///
    /// @param runIds The runs being worked on by this node.
    void heartbeat(Collection<Integer> runIds);

    /// @param runIds Some runs.
    /// @return The status of each run that exists.
    Map<Integer, AiChatRunStatus> getStatuses(Collection<Integer> runIds);

    /// Marks active runs whose heartbeat is older than a time as interrupted, as the node working on them must
    /// have stopped.
    ///
    /// @param staleBeforeMs Runs with a heartbeat before this time are interrupted.
    /// @return The runs that were interrupted, as they were before.
    List<AiChatRun> interruptStale(long staleBeforeMs);

    /// @param sessionIdHash A hash of an HTTP session's id.
    /// @return The active runs started from that session.
    List<AiChatRun> getActiveBySession(String sessionIdHash);

    /// Corrects the stop reason of the runs started from a session that were stopped with one reason since a
    /// time, e.g. runs terminated because their session ended, once it is known whether the user signed out.
    ///
    /// @param sessionIdHash A hash of an HTTP session's id.
    /// @param from          The reason to correct.
    /// @param to            The correct reason.
    /// @param endedAfterMs  Only runs that ended after this time are corrected.
    void correctStopReason(String sessionIdHash,
                           AiChatRunStopReason from,
                           AiChatRunStopReason to,
                           long endedAfterMs);

    /// Records whether a run is unattended, i.e. its user has gone.
    ///
    /// @param runId      The run's id.
    /// @param unattended True if the run is unattended.
    void setUnattended(int runId, boolean unattended);

    /// @param userUuid A user.
    /// @return How many of the user's runs are being worked on (RUNNING or STOPPING), across the cluster.
    int countWorking(String userUuid);

    /// Deletes all of a chat's runs, e.g. because the chat is being deleted.
    ///
    /// @param chatId The chat.
    void deleteByChat(int chatId);
}
