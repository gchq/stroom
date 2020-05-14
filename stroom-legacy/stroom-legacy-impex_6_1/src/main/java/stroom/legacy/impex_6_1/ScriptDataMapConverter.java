package stroom.legacy.impex_6_1;

import stroom.dashboard.impl.script.ScriptSerialiser;
import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Script;
import stroom.script.shared.ScriptDoc;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

class ScriptDataMapConverter implements DataMapConverter {
//    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptDataMapConverter.class);

    private final ScriptSerialiser serialiser;

    @Inject
    ScriptDataMapConverter(final ScriptSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            try {
                final Script oldScript = new Script();
                LegacyXmlSerialiser.performImport(oldScript, dataMap);

                final ScriptDoc document = new ScriptDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldScript.getCreateTime());
                document.setUpdateTimeMs(oldScript.getUpdateTime());
                document.setCreateUser(oldScript.getCreateUser());
                document.setUpdateUser(oldScript.getUpdateUser());

                final stroom.legacy.model_6_1.DocRefs docRefs = LegacyXmlSerialiser.getDocRefsFromLegacyXml(oldScript.getDependenciesXML());
                document.setDependencies(MappingUtil.map(docRefs));

                result = serialiser.write(document);
                if (dataMap.containsKey("resource.js")) {
                    result.put("js", dataMap.remove("resource.js"));
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
