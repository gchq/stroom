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

package stroom.ai.impl.tools.time;

import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// The time zone of the browser each Stroom AI chat was last asked from, as the chat client sends it with each
/// question, for the tools' `local` time zone ([UserTimeZones]). Held in memory on the node the chat is asked on,
/// which is the node its run works on: a run resumed after a restart, before the next question, doesn't know it.
@Singleton
public class ChatBrowserZones {

    private final Map<Integer, String> zones = new ConcurrentHashMap<>();

    /// Records the zone a chat was asked from.
    ///
    /// @param chatId The chat, or null for none (nothing is recorded).
    /// @param zoneId The browser's time zone id, or null/blank if the client didn't send one (the last one sent is
    ///               kept).
    public void record(final Integer chatId, final String zoneId) {
        if (chatId != null && NullSafe.isNonBlankString(zoneId)) {
            zones.put(chatId, zoneId.strip());
        }
    }

    /// @param chatId The chat.
    /// @return The time zone id of the browser the chat was last asked from, or null if not known.
    public String get(final int chatId) {
        return zones.get(chatId);
    }
}
