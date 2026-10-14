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

import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;

import java.util.List;
import java.util.Optional;

/// Stores 'Ask Stroom AI' memory. Changes to a piece of memory are made with compare-and-set updates on its
/// version, as several runs may share the same memory.
public interface AiMemoryDao {

    /// @param userUuid The user the memory belongs to.
    /// @param scope    The scope.
    /// @param scopeKey The scope key ("" for USER scope).
    /// @return The memory in the scope, by name.
    List<AiMemory> list(String userUuid, AiMemoryScope scope, String scopeKey);

    /// @return The named memory, if there is one.
    Optional<AiMemory> get(String userUuid, AiMemoryScope scope, String scopeKey, String name);

    /// @return The memory with the id, if there is one.
    Optional<AiMemory> get(int id);

    /// Creates a piece of memory.
    ///
    /// @param userUuid   The user it belongs to.
    /// @param scope      The scope.
    /// @param scopeKey   The scope key ("" for USER scope).
    /// @param name       The name.
    /// @param content    The content.
    /// @param updateUser Who is making the change.
    /// @return The memory, or empty if one with the name already exists.
    Optional<AiMemory> create(String userUuid,
                              AiMemoryScope scope,
                              String scopeKey,
                              String name,
                              String content,
                              String updateUser);

    /// Changes a piece of memory's content, if it has not been changed since it was read.
    ///
    /// @param id              The id.
    /// @param expectedVersion The version it was read at.
    /// @param content         The new content.
    /// @param updateUser      Who is making the change.
    /// @return The memory as changed, or empty if it has been changed or deleted since it was read.
    Optional<AiMemory> update(int id, int expectedVersion, String content, String updateUser);

    /// Deletes a piece of memory, if it has not been changed since it was read.
    ///
    /// @return True if it was deleted.
    boolean delete(int id, int expectedVersion);

    /// Deletes a chat's memory, e.g. because the chat is being deleted.
    void deleteChatMemory(int chatId);
}
