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

package stroom.pipeline.legacy;

import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;
import stroom.util.string.EncodingUtil;
import stroom.util.xml.XMLMarshallerUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class PipelineDataMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineDataMigration.class);
    private static final String JSON = "json";
    private static final String XML = "xml";

    private final JAXBContext jaxbContext;

    public PipelineDataMigration() {
        try {
            jaxbContext = JAXBContext.newInstance(PipelineData.class);
        } catch (final JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public boolean migrate(final ImportExportDocument importExportDocument) {
        try {
            if (importExportDocument != null) {
                final ImportExportAsset xmlAsset = importExportDocument.removeExtAsset(XML);
                if (xmlAsset != null) {
                    final byte[] xmlData = xmlAsset.getInputData();
                    if (xmlData != null) {
                        final String xml = EncodingUtil.asString(xmlData);
                        final PipelineData pipelineData =
                                XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
                        final String json = JsonUtil.writeValueAsString(pipelineData);
                        final stroom.pipeline.shared.data.PipelineData newData =
                                JsonUtil.readValue(json, stroom.pipeline.shared.data.PipelineData.class);
                        final stroom.pipeline.shared.data.PipelineData cleaned =
                                new stroom.pipeline.shared.data.PipelineDataBuilder(newData).build();
                        final String cleanedJson = JsonUtil.writeValueAsString(cleaned);

                        final ImportExportAsset migratedAsset =
                                new ByteArrayImportExportAsset(JSON, EncodingUtil.asBytes(cleanedJson));
                        importExportDocument.addExtAsset(migratedAsset);
                        return true;
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

//    public stroom.pipeline.shared.data.PipelineData migrate(final String xml) {
//        if (xml != null) {
//            if (xml.startsWith("<")) {
//                final PipelineData pipelineData =
//                        XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
//                final String json = JsonUtil.writeValueAsString(pipelineData);
//                final stroom.pipeline.shared.data.PipelineData newData =
//                        JsonUtil.readValue(json, stroom.pipeline.shared.data.PipelineData.class);
//                final stroom.pipeline.shared.data.PipelineData cleaned =
//                        new stroom.pipeline.shared.data.PipelineDataBuilder(newData).build();
//                final String cleanedJson = JsonUtil.writeValueAsString(cleaned);
//                return cleaned;
//            } else {
//                return JsonUtil.readValue(xml, stroom.pipeline.shared.data.PipelineData.class);
//            }
//        }
//        return new PipelineDataBuilder().build();
//    }

//    public void migrate(final Path file) {
//        try {
//            final String data = Files.readString(file);
//            if (data.startsWith("<")) {
//                final String json = JsonUtil.writeValueAsString(new PipelineDataMigration().migrate(data));
//                final String name = file.getFileName().toString().replaceAll(".xml$", ".json");
//                final Path path = file.getParent().resolve(name);
//                Files.writeString(path, json);
//            }
//        } catch (final Exception e) {
//            System.err.println(e.getMessage());
//        }
//    }
}
