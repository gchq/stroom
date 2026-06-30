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

package stroom.docstore.mock;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;

import java.util.List;
import java.util.Optional;

public class MockDocFinder implements DocFinder {

    @Override
    public List<DocRef> findByName(final String type, final String nameFilter, final boolean allowWildCards) {
        return List.of();
    }

    @Override
    public List<DocRef> findByNames(final String type, final List<String> nameFilters, final boolean allowWildCards) {
        return List.of();
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        if (docRef == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(docRef.getName());
    }
}
