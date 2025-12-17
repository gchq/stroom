/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.langchain.impl;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class AutoExpiringChatMemoryStore implements ChatMemoryStore {

    private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();
    private final Map<Object, Instant> memoryAgeMap = new ConcurrentHashMap<>();
    private final Duration timeToLive;

    public AutoExpiringChatMemoryStore(final Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public void prune() {
        final Instant expiryCutoff = Instant.now().minus(timeToLive);
        for (final Entry<Object, Instant> entry : memoryAgeMap.entrySet()) {
            if (entry.getValue().isBefore(expiryCutoff)) {
                messagesByMemoryId.remove(entry.getKey());
                memoryAgeMap.remove(entry.getKey());
            }
        }
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        return messagesByMemoryId.compute(memoryId, (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            touchMemoryId(k);
            return v;
        });
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        messagesByMemoryId.put(memoryId, messages);
        touchMemoryId(memoryId);
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        messagesByMemoryId.remove(memoryId);
        memoryAgeMap.remove(memoryId);
    }

    /**
     * Update the last access time of the chat memory, so it doesn't age off
     */
    private void touchMemoryId(final Object memoryId) {
        memoryAgeMap.compute(memoryId, (k, v) -> {
            v = Instant.now();
            return v;
        });
    }
}
