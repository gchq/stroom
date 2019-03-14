package stroom.core.migration._V07_00_00.doc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.core.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.core.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.core.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.migration._V07_00_00.entity.util._V07_00_00_XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class _V07_00_00_PipelineSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_PipelineDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_PipelineSerialiser.class);

    private static final String XML = "xml";

//    private final ObjectMapper mapper;

    public _V07_00_00_PipelineSerialiser() {
        super(_V07_00_00_PipelineDoc.class);
//        mapper = getMapper(true);
    }

    @Override
    public _V07_00_00_PipelineDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_PipelineDoc document = super.read(data);

        final String xml = _V07_00_00_EncodingUtil.asString(data.get(XML));
        final _V07_00_00_PipelineData pipelineData = getPipelineDataFromXml(xml);
        document.setPipelineData(pipelineData);

        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_PipelineDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        _V07_00_00_PipelineData pipelineData = document.getPipelineData();
        if (pipelineData != null) {
            data.put(XML, _V07_00_00_EncodingUtil.asBytes(getXmlFromPipelineData(pipelineData)));
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

    public _V07_00_00_PipelineData getPipelineDataFromXml(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_PipelineData.class);
                return _V07_00_00_XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_PipelineData.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal pipeline config", e);
            }
        }

        return null;
    }

    public String getXmlFromPipelineData(final _V07_00_00_PipelineData pipelineData) {
        if (pipelineData != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_PipelineData.class);
                return _V07_00_00_XMLMarshallerUtil.marshal(jaxbContext, _V07_00_00_XMLMarshallerUtil.removeEmptyCollections(pipelineData));
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to marshal pipeline config", e);
            }
        }

        return null;
    }

    public _V07_00_00_DocRef getDocRefFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_DocRef.class);
                return _V07_00_00_XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_DocRef.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}