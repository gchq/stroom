package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.docref.DocRef;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class PipelineSerialiser extends JsonSerialiser2<PipelineDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineSerialiser.class);

    private static final String XML = "xml";

//    private final ObjectMapper mapper;

    public PipelineSerialiser() {
        super(PipelineDoc.class);
//        mapper = getMapper(true);
    }

    @Override
    public PipelineDoc read(final Map<String, byte[]> data) throws IOException {
        final PipelineDoc document = super.read(data);

        final String xml = EncodingUtil.asString(data.get(XML));
        final PipelineData pipelineData = getPipelineDataFromXml(xml);
        document.setPipelineData(pipelineData);

        return document;
    }

    @Override
    public Map<String, byte[]> write(final PipelineDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        PipelineData pipelineData = document.getPipelineData();
        if (pipelineData != null) {
            data.put(XML, EncodingUtil.asBytes(getXmlFromPipelineData(pipelineData)));
        }
        return data;
    }

//    public PipelineData getPipelineDataFromJson(final String json) throws IOException {
//        if (json != null) {
//            return mapper.readValue(new StringReader(json), PipelineData.class);
//        }
//        return null;
//    }
//
//    public String getXmlFromPipelineData(final PipelineData pipelineData) throws IOException {
//        if (pipelineData != null) {
//            final StringWriter stringWriter = new StringWriter();
//            mapper.writeValue(stringWriter, pipelineData);
//            return stringWriter.toString();
//        }
//        return null;
//    }

    public PipelineData getPipelineDataFromXml(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(PipelineData.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal pipeline config", e);
            }
        }

        return null;
    }

    public String getXmlFromPipelineData(final PipelineData pipelineData) {
        if (pipelineData != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(PipelineData.class);
                return XMLMarshallerUtil.marshal(jaxbContext, XMLMarshallerUtil.removeEmptyCollections(pipelineData));
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to marshal pipeline config", e);
            }
        }

        return null;
    }

    public DocRef getDocRefFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(DocRef.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, DocRef.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}