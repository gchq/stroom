package stroom.docstore.impl.db.migration.pipeline.legacy;

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
                    stroom.docstore.impl.db.migration.pipeline.legacy.xml.PipelineData.class);
        } catch (final JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public String xmlToJson(final String xml) {
        try {
            final stroom.docstore.impl.db.migration.pipeline.legacy.xml.PipelineData pipelineData =
                    XMLMarshallerUtil.unmarshal(jaxbContext,
                            stroom.docstore.impl.db.migration.pipeline.legacy.xml.PipelineData.class, xml);
            final String json = JsonUtil.writeValueAsString(pipelineData);
            final stroom.docstore.impl.db.migration.pipeline.legacy.json.PipelineData newData =
                    JsonUtil.readValue(json, stroom.docstore.impl.db.migration.pipeline.legacy.json.PipelineData.class);
            final stroom.docstore.impl.db.migration.pipeline.legacy.json.PipelineData cleaned =
                    new stroom.docstore.impl.db.migration.pipeline.legacy.json.PipelineDataBuilder(newData).build();
            return JsonUtil.writeValueAsString(cleaned);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
