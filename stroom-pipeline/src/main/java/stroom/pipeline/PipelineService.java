/*
 * Copyright 2021 Crown Copyright
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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;

import java.util.List;

public interface PipelineService {

    PipelineDoc fetch(final String uuid);

    PipelineDoc update(final String uuid, final PipelineDoc doc);

    Boolean savePipelineXml(final DocRef pipeline, String xml);

    String fetchPipelineXml(final DocRef pipeline);

    List<PipelineData> fetchPipelineData(final DocRef pipeline);

    /**
     * @param nameFilter Exact match or wild-carded with a '*' character
     * @return List of Pipeline UUIDs corresponding to the pipelines that match nameFilter
     */
    List<String> findUuidsByName(final String nameFilter);
}
