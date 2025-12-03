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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineLayer;

import java.util.List;

public interface PipelineService {

    PipelineDoc fetch(final String uuid);

    PipelineDoc update(final String uuid, final PipelineDoc doc);

    Boolean savePipelineJson(final DocRef pipeline, String json);

    String fetchPipelineJson(final DocRef pipeline);

    List<PipelineLayer> fetchPipelineLayers(final DocRef pipeline);
}
