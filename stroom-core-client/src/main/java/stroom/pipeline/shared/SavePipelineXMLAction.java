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

import stroom.dispatch.shared.Action;
import stroom.util.shared.VoidResult;

public class SavePipelineXMLAction extends Action<VoidResult> {
    private static final long serialVersionUID = -1773544031158236156L;

    private long pipelineId;
    private String xml;

    public SavePipelineXMLAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public SavePipelineXMLAction(final long pipelineId, final String xml) {
        this.pipelineId = pipelineId;
        this.xml = xml;
    }

    public long getPipelineId() {
        return pipelineId;
    }

    public String getXml() {
        return xml;
    }

    @Override
    public String getTaskName() {
        return "FetchPipelineDataAction - fetchPipelineData()";
    }
}
