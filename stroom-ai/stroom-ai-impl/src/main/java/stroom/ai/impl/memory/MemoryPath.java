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

package stroom.ai.impl.memory;

import stroom.ai.shared.AiMemoryScope;

import java.util.Optional;
import java.util.regex.Pattern;

/// A path to a piece of memory, as the agent sees it:
///
/// - {@code /memories/<name>}: the user's memory, loaded into every chat;
/// - {@code /memories/session/<name>}: the chat's own memory.
///
/// A name is 1-64 letters, digits, '.', '_' or '-', and may not start with '.', so a path can never reach
/// anything else.
///
/// @param scope The scope.
/// @param name  The name.
public record MemoryPath(AiMemoryScope scope, String name) {

    public static final String USER_PREFIX = "/memories/";
    public static final String CHAT_PREFIX = "/memories/session/";

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}");

    /// @param path A path given by the agent.
    /// @return The memory it refers to, or empty if it is not a valid memory path.
    public static Optional<MemoryPath> parse(final String path) {
        if (path == null) {
            return Optional.empty();
        }
        final String trimmed = path.strip();
        final AiMemoryScope scope;
        final String name;
        if (trimmed.startsWith(CHAT_PREFIX)) {
            scope = AiMemoryScope.CHAT;
            name = trimmed.substring(CHAT_PREFIX.length());
        } else if (trimmed.startsWith(USER_PREFIX)) {
            scope = AiMemoryScope.USER;
            name = trimmed.substring(USER_PREFIX.length());
        } else {
            return Optional.empty();
        }
        return isValidName(name)
                ? Optional.of(new MemoryPath(scope, name))
                : Optional.empty();
    }

    /// @param name A name.
    /// @return True if it is a valid memory name.
    public static boolean isValidName(final String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /// @return The path, as the agent sees it.
    public String path() {
        return (scope == AiMemoryScope.CHAT
                ? CHAT_PREFIX
                : USER_PREFIX) + name;
    }

    /// @param chatId The chat.
    /// @return The scope key: the chat's id for CHAT scope, otherwise empty.
    public String scopeKey(final int chatId) {
        return scope == AiMemoryScope.CHAT
                ? String.valueOf(chatId)
                : "";
    }
}
