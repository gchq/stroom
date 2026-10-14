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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Store names that must be unique across every Plan B store type.
///
/// Plan B resolves a store by name alone, across every type in [PlanBDocumentTypes], and fails when
/// the name matches more than one document (see [PlanBDocCacheImpl]). So two stores that share a name
/// break lookups of both, whatever their types. Each document store used to check only its own type,
/// so a Plan B store and a Floor Map event store could share a name.
///
/// This answers for the *other* Plan B types. A document store checks its own type itself, directly
/// against its persistence, which is uncached - so a store created a moment ago, perhaps on another
/// node, is seen - and includes stores its user cannot see.
///
/// Every lookup here is made as the processing user, as [PlanBDocCacheImpl] resolves names: a clash
/// with a store the user cannot see breaks resolution just the same. Only whether a name is taken is
/// returned, never which store has it.
@Singleton
public class PlanBStoreNames {

    private final SecurityContext securityContext;
    private final Provider<DocFinder> docFinderProvider;
    private final Set<String> planBDocumentTypes;

    @Inject
    public PlanBStoreNames(final SecurityContext securityContext,
                           final Provider<DocFinder> docFinderProvider,
                           @PlanBDocumentTypes final Set<String> planBDocumentTypes) {
        this.securityContext = securityContext;
        this.docFinderProvider = docFinderProvider;
        this.planBDocumentTypes = planBDocumentTypes;
    }

    /// Whether a store of a Plan B type other than `ownType` already has this name.
    ///
    /// The match is exact and case-sensitive, as Plan B's own resolution is.
    ///
    /// @param name    The proposed name.
    /// @param ownType The type of the store being named, which its own document store checks.
    /// @return `true` if a store of another type has the name.
    public boolean isTakenByAnotherType(final String name, final String ownType) {
        return securityContext.asProcessingUserResult(() -> otherTypes(ownType)
                .anyMatch(type -> !docFinderProvider.get().findByName(type, name).isEmpty()));
    }

    /// The names of every store of a Plan B type other than `ownType`.
    ///
    /// For choosing a name that is free, as copying does; [#isTakenByAnotherType] answers whether
    /// one name is.
    ///
    /// @param ownType The type of the store being named, which its own document store checks.
    /// @return Every name in use by a store of another type.
    public Set<String> namesOfOtherTypes(final String ownType) {
        return securityContext.asProcessingUserResult(() -> otherTypes(ownType)
                .flatMap(type -> docFinderProvider.get().findByName(type, "*", true).stream())
                .map(DocRef::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    private Stream<String> otherTypes(final String ownType) {
        return NullSafe.set(planBDocumentTypes)
                .stream()
                .filter(type -> !Objects.equals(type, ownType));
    }
}
