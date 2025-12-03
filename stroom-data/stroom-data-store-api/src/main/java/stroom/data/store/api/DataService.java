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

package stroom.data.store.api;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.UploadDataRequest;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DataService {

    ResourceGeneration download(FindMetaCriteria criteria);

    ResourceKey upload(UploadDataRequest request);

    Map<String, String> metaAttributes(long id);

    List<DataInfoSection> info(long id);

    AbstractFetchDataResult fetch(final FetchDataRequest request);

    Set<String> getChildStreamTypes(final long id, final long partNo);
}
