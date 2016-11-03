/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.shared.data;

import stroom.entity.shared.DocRef;
import stroom.pipeline.shared.PipelineEntity;
import stroom.util.shared.SharedObject;

public class SourcePipeline implements SharedObject {
    private static final long serialVersionUID = -3209898449831302066L;

    private DocRef pipeline;

    public SourcePipeline() {
        // Default constructor necessary for GWT serialisation.
    }

    public SourcePipeline(final PipelineEntity pipeline) {
        this.pipeline = DocRef.create(pipeline);
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }
}
