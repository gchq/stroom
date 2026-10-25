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

package stroom.mcp.impl.tools;

import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// What a user chose in draft_cef_mapping's form, by build, so a later call for the same build isn't asked again
/// (not in the Stroom MCP server; seen: asked whether keys outside ArcSight's dictionary may be used on every call
/// of one CEF pipeline's building). Kept per user, in this server's memory only.
@Singleton
class CefChoices {

    // The most kept, the oldest forgotten first.
    private static final int MAX_ENTRIES = 1_000;

    private final SecurityContext securityContext;
    private final Map<String, Boolean> customKeys = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, false) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /// @param securityContext Says who the user is.
    @Inject
    CefChoices(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    /// @param build   The build, or null for a call outside one.
    /// @param allowed True if the user allowed keys outside ArcSight's CEF dictionary.
    void chooseCustomKeys(final String build, final boolean allowed) {
        customKeys.put(key(build), allowed);
    }

    /// @param build The build, or null for a call outside one.
    /// @return What the user chose for it, if they have.
    Optional<Boolean> customKeys(final String build) {
        return Optional.ofNullable(customKeys.get(key(build)));
    }

    private String key(final String build) {
        String user;
        try {
            user = NullSafe.string(NullSafe.get(securityContext.getUserRef(), UserRef::getUuid));
        } catch (final RuntimeException e) {
            user = "";
        }
        return user + "\n" + NullSafe.string(build);
    }
}
