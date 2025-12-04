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

package stroom.docrefinfo.mock;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.security.shared.DocumentPermission;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MockDocRefInfoService implements DocRefInfoService {

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return Optional.of(DocRefInfo.builder()
                .docRef(docRef)
                .build());
    }

    @Override
    public Optional<DocRefInfo> info(final String uuid) {
        return Optional.of(DocRefInfo.builder()
                .docRef(new DocRef("UNKNOWN", uuid))
                .build());
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return Optional.ofNullable(docRef.getName());
    }

    @Override
    public List<DocRef> findByName(final String type, final String nameFilter, final boolean allowWildCards) {
        return Collections.emptyList();
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards) {
        return Collections.emptyList();
    }

    @Override
    public List<DocRef> findByType(final String type) {
        return Collections.emptyList();
    }

    @Override
    public DocRef decorate(final DocRef docRef, final boolean force) {
        return docRef;
    }

    @Override
    public List<DocRef> decorate(final List<DocRef> docRefs) {
        return docRefs;
    }

    @Override
    public DocRef decorate(final DocRef docRef,
                           final boolean force,
                           final Set<DocumentPermission> requiredPermissions) {
        return docRef;
    }
}
