/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.api;

/**
 * Terminates a user's HTTP sessions across the whole cluster. Exposed as an API so that credential flows in
 * other modules (e.g. a password reset) can end a user's sessions without depending on the session
 * implementation.
 */
public interface UserSessionEvictor {

    /**
     * Invalidate every HTTP session belonging to the given user, on every node in the cluster, optionally
     * sparing one session (typically the caller's own).
     *
     * @param userSubjectId   the subject id of the user whose sessions should be terminated.
     * @param exceptSessionId a session id to spare, or {@code null} to terminate all of the user's sessions.
     */
    void evictUserSessions(String userSubjectId, String exceptSessionId);
}
