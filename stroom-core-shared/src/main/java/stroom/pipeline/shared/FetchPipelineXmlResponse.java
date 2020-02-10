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

import stroom.docref.DocRef;

public class FetchPipelineXmlResponse {
    private DocRef pipeline;
    private String xml;

    public FetchPipelineXmlResponse() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchPipelineXmlResponse(final DocRef pipeline, final String xml) {
        this.pipeline = pipeline;
        this.xml = xml;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(final String xml) {
        this.xml = xml;
    }
}
