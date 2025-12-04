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

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.util.json.JsonUtil;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class PipelineSerialiser implements DocumentSerialiser2<PipelineDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineSerialiser.class);
    private static final String JSON = "json";

    private final Serialiser2<PipelineDoc> delegate;

    @Inject
    public PipelineSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(PipelineDoc.class);
    }

    @Override
    public PipelineDoc read(final Map<String, byte[]> data) throws IOException {
        final PipelineDoc document = delegate.read(data);
        final String json = EncodingUtil.asString(data.get(JSON));
        final PipelineData pipelineData = getPipelineDataFromJson(json);
        document.setPipelineData(pipelineData);

        return document;
    }

    @Override
    public Map<String, byte[]> write(final PipelineDoc document) throws IOException {
        PipelineData pipelineData = document.getPipelineData();
        document.setPipelineData(null);

        final Map<String, byte[]> data = delegate.write(document);

        // If the pipeline doesn't have data, it may be a new pipeline, create a blank one.
        if (pipelineData == null) {
            pipelineData = new PipelineDataBuilder().build();
        }

        data.put(JSON, EncodingUtil.asBytes(getJsonFromPipelineData(pipelineData)));

        document.setPipelineData(pipelineData);

        return data;
    }

    public PipelineData getPipelineDataFromJson(final String json) {
        if (json != null) {
            try {
                return JsonUtil.readValue(json, PipelineData.class);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to unmarshal pipeline config", e);
            }
        }

        return null;
    }

    public String getJsonFromPipelineData(final PipelineData pipelineData) {
        if (pipelineData != null) {
            try {
                return JsonUtil.writeValueAsString(pipelineData);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to marshal pipeline config", e);
            }
        }

        return null;
    }
}
