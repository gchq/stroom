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

package stroom.pipeline.shared;

import stroom.entity.shared.Action;
import stroom.query.api.v1.DocRef;
import stroom.util.shared.SharedString;

public class FetchPipelineXMLAction extends Action<SharedString> {
    private static final long serialVersionUID = -1773544031158236156L;

    private DocRef pipeline;

    public FetchPipelineXMLAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchPipelineXMLAction(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    @Override
    public String getTaskName() {
        return "FetchPipelineDataAction - fetchPipelineData()";
    }
}
