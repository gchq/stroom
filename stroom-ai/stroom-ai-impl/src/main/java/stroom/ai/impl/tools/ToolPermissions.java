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

package stroom.ai.impl.tools;

import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The permissions a [StroomTool] needs, as metadata about it, e.g. for an MCP endpoint to say which tools a user
/// can make use of. It is never shown to the model, and it enforces nothing: each tool still makes its own checks
/// (failing closed), as the user's own screens do, and every tool is still offered whatever the user's permissions
/// (it refuses when called). `TestToolPermissionMetadata` checks it against the checks the tools make.
///
/// @param requiredAppPermissions  The app permissions without which the tool refuses outright, e.g. Manage Tasks for
///                                server_tasks.
/// @param unlockingAppPermissions The app permissions that unlock parts of what the tool returns, without which it
///                                says that part needs them, e.g. Manage Processors for the processor filters
///                                folder_contents lists.
/// @param documentPermissions     The document permissions the tool checks on each document it reads or uses (View
///                                or Use). These are checked per document, so a user without them on one document
///                                can still use the tool on others.
/// @param note                    Anything else about the tool's permissions, e.g. checks Stroom makes on its
///                                behalf, or null.
public record ToolPermissions(Set<AppPermission> requiredAppPermissions,
                              Set<AppPermission> unlockingAppPermissions,
                              Set<DocumentPermission> documentPermissions,
                              String note) {

    /// A tool that checks no permission of its own.
    public static final ToolPermissions NONE = builder().build();

    /// @param requiredAppPermissions  The app permissions without which the tool refuses outright.
    /// @param unlockingAppPermissions The app permissions that unlock parts of what the tool returns.
    /// @param documentPermissions     The document permissions the tool checks on each document.
    /// @param note                    Anything else about the tool's permissions, or null.
    public ToolPermissions {
        requiredAppPermissions = immutable(requiredAppPermissions, AppPermission.class);
        unlockingAppPermissions = immutable(unlockingAppPermissions, AppPermission.class);
        documentPermissions = immutable(documentPermissions, DocumentPermission.class);
    }

    /// @return A builder for a tool's permissions.
    public static Builder builder() {
        return new Builder();
    }

    /// @return Every app permission the tool checks: those it requires and those that unlock parts of its output.
    public Set<AppPermission> appPermissions() {
        final Set<AppPermission> all = EnumSet.noneOf(AppPermission.class);
        all.addAll(requiredAppPermissions);
        all.addAll(unlockingAppPermissions);
        return Collections.unmodifiableSet(all);
    }

    /// @return True if the tool checks no permission of its own.
    public boolean isNone() {
        return requiredAppPermissions.isEmpty()
               && unlockingAppPermissions.isEmpty()
               && documentPermissions.isEmpty();
    }

    private static <E extends Enum<E>> Set<E> immutable(final Collection<E> values, final Class<E> type) {
        final Set<E> copy = EnumSet.noneOf(type);
        if (values != null) {
            copy.addAll(values);
        }
        return Collections.unmodifiableSet(copy);
    }

    /// Builds a [ToolPermissions].
    public static final class Builder {

        private final Set<AppPermission> required = EnumSet.noneOf(AppPermission.class);
        private final Set<AppPermission> unlocking = EnumSet.noneOf(AppPermission.class);
        private final Set<DocumentPermission> documents = EnumSet.noneOf(DocumentPermission.class);
        private String note;

        private Builder() {
        }

        /// @param permissions App permissions without which the tool refuses outright.
        /// @return This builder.
        public Builder requires(final AppPermission... permissions) {
            required.addAll(List.of(permissions));
            return this;
        }

        /// @param permissions App permissions that unlock parts of what the tool returns.
        /// @return This builder.
        public Builder unlockedBy(final AppPermission... permissions) {
            unlocking.addAll(List.of(permissions));
            return this;
        }

        /// @param permissions Document permissions the tool checks on each document it reads or uses.
        /// @return This builder.
        public Builder checksDocuments(final DocumentPermission... permissions) {
            documents.addAll(List.of(permissions));
            return this;
        }

        /// @param note Anything else about the tool's permissions.
        /// @return This builder.
        public Builder note(final String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        /// @return The permissions.
        public ToolPermissions build() {
            return new ToolPermissions(required, unlocking, documents, note);
        }
    }
}
