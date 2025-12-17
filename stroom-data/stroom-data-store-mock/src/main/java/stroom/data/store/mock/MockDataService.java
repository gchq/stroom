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

package stroom.data.store.mock;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.DataService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockDataService implements DataService {

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {
        return null;
    }

    @Override
    public Map<String, String> metaAttributes(final long id) {
        return Map.of();
    }

    @Override
    public List<DataInfoSection> info(final long id) {
        return List.of();
    }

    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {
        return null;
    }

    @Override
    public Set<String> getChildStreamTypes(final long id, final long partNo) {
        return Set.of();
    }
}
