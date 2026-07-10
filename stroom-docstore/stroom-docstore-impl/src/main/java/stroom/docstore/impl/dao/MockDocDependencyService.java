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

package stroom.docstore.impl.dao;

import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.ResultPage;

import java.util.Map;
import java.util.Set;

public class MockDocDependencyService implements DocDependencyService {

    @Override
    public void setDependencies(final DocRef docRef, final Set<DocRef> dependencies) {

    }

    @Override
    public void removeDependencies(final DocRef docRef) {

    }

    @Override
    public void propagateName(final DocRef docRef) {

    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return null;
    }

    @Override
    public Map<DocRef, Set<DocRef>> fetchBrokenDependencies(final Set<String> pseudoRefUuids) {
        return Map.of();
    }

    @Override
    public void invalidateBrokenDependencyCache() {

    }

    @Override
    public Set<DocRef> getDependantsOf(final DocRef docRef) {
        return Set.of();
    }
}