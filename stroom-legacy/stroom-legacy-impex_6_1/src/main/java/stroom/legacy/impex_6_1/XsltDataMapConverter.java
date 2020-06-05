package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.XSLT;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltSerialiser;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
@Deprecated
class XsltDataMapConverter implements DataMapConverter {
    private final XsltSerialiser serialiser;

    @Inject
    XsltDataMapConverter(final XsltSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (!dataMap.containsKey("meta")) {
            try {
                final XSLT oldXslt = new XSLT();
                LegacyXmlSerialiser.performImport(oldXslt, dataMap);

                final XsltDoc document = new XsltDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldXslt.getCreateTime());
                document.setUpdateTimeMs(oldXslt.getUpdateTime());
                document.setCreateUser(oldXslt.getCreateUser());
                document.setUpdateUser(oldXslt.getUpdateUser());

                document.setDescription(oldXslt.getDescription());
                document.setData(oldXslt.getData());

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
