package stroom.db.migration.doc.feed;

import stroom.db.migration.doc.JsonSerialiser2;

public class FeedSerialiser extends JsonSerialiser2<FeedDoc> {
//    private static final Logger LOGGER = LoggerFactory.getLogger(FeedSerialiser.class);
//
//    private static final String XML = "xml";

//    private final ObjectMapper mapper;

    public FeedSerialiser() {
        super(FeedDoc.class);
//        mapper = getMapper(true);
    }

//    @Override
//    public FeedDoc read(final Map<String, byte[]> data) throws IOException {
//        final FeedDoc document = super.read(data);
//
////        final String xml = EncodingUtil.asString(data.get(XML));
////        final PipelineData pipelineData = getPipelineDataFromXml(xml);
////        document.setPipelineData(pipelineData);
//
//        return document;
//    }
//
//    @Override
//    public Map<String, byte[]> write(final FeedDoc document) throws IOException {
//        final Map<String, byte[]> data = super.write(document);
//
//        PipelineData pipelineData = document.getPipelineData();
//        if (pipelineData != null) {
//            data.put(XML, EncodingUtil.asBytes(getXmlFromPipelineData(pipelineData)));
//        }
//        return data;
//    }

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
//
//    public PipelineData getPipelineDataFromXml(final String xml) {
//        if (xml != null) {
//            try {
//                final JAXBContext jaxbContext = JAXBContext.newInstance(PipelineData.class);
//                return XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, xml);
//            } catch (final JAXBException | RuntimeException e) {
//                LOGGER.error("Unable to unmarshal pipeline config", e);
//            }
//        }
//
//        return null;
//    }
//
//    public String getXmlFromPipelineData(final PipelineData pipelineData) {
//        if (pipelineData != null) {
//            try {
//                final JAXBContext jaxbContext = JAXBContext.newInstance(PipelineData.class);
//                return XMLMarshallerUtil.marshal(jaxbContext, XMLMarshallerUtil.removeEmptyCollections(pipelineData));
//            } catch (final JAXBException | RuntimeException e) {
//                LOGGER.error("Unable to marshal pipeline config", e);
//            }
//        }
//
//        return null;
//    }
//
//    public DocRef getDocRefFromLegacyXML(final String xml) {
//        if (xml != null) {
//            try {
//                final JAXBContext jaxbContext = JAXBContext.newInstance(DocRef.class);
//                return XMLMarshallerUtil.unmarshal(jaxbContext, DocRef.class, xml);
//            } catch (final JAXBException | RuntimeException e) {
//                LOGGER.error("Unable to unmarshal dashboard config", e);
//            }
//        }
//
//        return null;
//    }
}