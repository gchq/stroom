package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.xmlschema.XmlSchemaSerialiser;
import stroom.util.shared.Severity;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
class XmlSchemaDataMapConverter implements DataMapConverter {
    private final XmlSchemaSerialiser serialiser;

    @Inject
    XmlSchemaDataMapConverter(final XmlSchemaSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            try {
                final stroom.legacy.model_6_1.XMLSchema oldXmlSchema = new stroom.legacy.model_6_1.XMLSchema();
                LegacyXmlSerialiser.performImport(oldXmlSchema, dataMap);

                final XmlSchemaDoc document = new XmlSchemaDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldXmlSchema.getCreateTime());
                document.setUpdateTimeMs(oldXmlSchema.getUpdateTime());
                document.setCreateUser(oldXmlSchema.getCreateUser());
                document.setUpdateUser(oldXmlSchema.getUpdateUser());

                document.setDescription(oldXmlSchema.getDescription());
                document.setNamespaceURI(oldXmlSchema.getNamespaceURI());
                document.setSystemId(oldXmlSchema.getSystemId());
                document.setData(oldXmlSchema.getData());
                document.setDeprecated(oldXmlSchema.isDeprecated());
                document.setSchemaGroup(oldXmlSchema.getSchemaGroup());

                result = serialiser.write(document);
//                if (dataMap.containsKey("data.xsd")) {
//                    result.put("xsd", dataMap.remove("data.xsd"));
//                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
