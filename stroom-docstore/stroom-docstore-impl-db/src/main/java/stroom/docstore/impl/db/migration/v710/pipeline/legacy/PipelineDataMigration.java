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

package stroom.docstore.impl.db.migration.v710.pipeline.legacy;

import stroom.docstore.impl.db.migration.v710.pipeline.legacy.json.PipelineData;
import stroom.docstore.impl.db.migration.v710.pipeline.legacy.json.PipelineDataBuilder;
import stroom.util.json.JsonUtil;
import stroom.util.xml.XMLMarshallerUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

@Deprecated
public class PipelineDataMigration {

    private final JAXBContext jaxbContext;

    public PipelineDataMigration() {
        try {
            jaxbContext = JAXBContext.newInstance(
                    stroom.docstore.impl.db.migration.v710.pipeline.legacy.xml.PipelineData.class);
        } catch (final JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public String xmlToJson(final String xml) {
        try {
            final stroom.docstore.impl.db.migration.v710.pipeline.legacy.xml.PipelineData pipelineData =
                    XMLMarshallerUtil.unmarshal(jaxbContext,
                            stroom.docstore.impl.db.migration.v710.pipeline.legacy.xml.PipelineData.class, xml);
            final String json = JsonUtil.writeValueAsString(pipelineData);
            final PipelineData newData =
                    JsonUtil.readValue(json, PipelineData.class);
            final PipelineData cleaned =
                    new PipelineDataBuilder(newData).build();
            return JsonUtil.writeValueAsString(cleaned);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
