package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.string.EncodingUtil;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class PipelineSerialiser implements DocumentSerialiser2<PipelineDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineSerialiser.class);

    private static final String XML = "xml";

    private final Serialiser2<PipelineDoc> delegate;

    @Inject
    public PipelineSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(PipelineDoc.class);
    }

    @Override
    public PipelineDoc read(final Map<String, byte[]> data) throws IOException {
        final PipelineDoc document = delegate.read(data);

        final String xml = EncodingUtil.asString(data.get(XML));
        final PipelineData pipelineData = getPipelineDataFromXml(xml);
        document.setPipelineData(pipelineData);

        return document;
    }

    @Override
    public Map<String, byte[]> write(final PipelineDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);

        PipelineData pipelineData = document.getPipelineData();

        // If the pipeline doesn't have data, it may be a new pipeline, create a blank one.
        if (pipelineData == null) {
            pipelineData = new PipelineData();
        }

        data.put(XML, EncodingUtil.asBytes(getXmlFromPipelineData(pipelineData)));

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