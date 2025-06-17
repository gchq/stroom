package stroom.pipeline.legacy;

import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.util.json.JsonUtil;
import stroom.util.string.EncodingUtil;
import stroom.util.xml.XMLMarshallerUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

    public boolean migrate(final Map<String, byte[]> data) {
        try {
            if (data != null) {
                final String xml = EncodingUtil.asString(data.remove(XML));
                if (xml != null) {
                    final PipelineData pipelineData =
                            XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
                    final String json = JsonUtil.writeValueAsString(pipelineData);
                    final stroom.pipeline.shared.data.PipelineData newData =
                            JsonUtil.readValue(json, stroom.pipeline.shared.data.PipelineData.class);
                    final stroom.pipeline.shared.data.PipelineData cleaned =
                            new stroom.pipeline.shared.data.PipelineDataBuilder(newData).build();
                    final String cleanedJson = JsonUtil.writeValueAsString(cleaned);

                    data.put(JSON, EncodingUtil.asBytes(cleanedJson));
                    return true;
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

    public stroom.pipeline.shared.data.PipelineData migrate(final String xml) {
        if (xml != null) {
            if (xml.startsWith("<")) {
                final PipelineData pipelineData =
                        XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
                final String json = JsonUtil.writeValueAsString(pipelineData);
                final stroom.pipeline.shared.data.PipelineData newData =
                        JsonUtil.readValue(json, stroom.pipeline.shared.data.PipelineData.class);
                final stroom.pipeline.shared.data.PipelineData cleaned =
                        new stroom.pipeline.shared.data.PipelineDataBuilder(newData).build();
                final String cleanedJson = JsonUtil.writeValueAsString(cleaned);
                return cleaned;
            } else {
                return JsonUtil.readValue(xml, stroom.pipeline.shared.data.PipelineData.class);
            }
        }
        return new PipelineDataBuilder().build();
    }
}
