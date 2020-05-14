package stroom.legacy.impex_6_1;

import stroom.dashboard.impl.visualisation.VisualisationSerialiser;
import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Visualisation;
import stroom.util.shared.Severity;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

class VisualisationDataMapConverter implements DataMapConverter {
    private final VisualisationSerialiser serialiser;

    @Inject
    VisualisationDataMapConverter(final VisualisationSerialiser serialiser) {
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
                final Visualisation oldVisualisation = new Visualisation();
                LegacyXmlSerialiser.performImport(oldVisualisation, dataMap);

                final VisualisationDoc document = new VisualisationDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldVisualisation.getCreateTime());
                document.setUpdateTimeMs(oldVisualisation.getUpdateTime());
                document.setCreateUser(oldVisualisation.getCreateUser());
                document.setUpdateUser(oldVisualisation.getUpdateUser());

                document.setDescription(oldVisualisation.getDescription());
                document.setFunctionName(oldVisualisation.getFunctionName());
                document.setSettings(oldVisualisation.getSettings());

                final stroom.legacy.model_6_1.DocRef scriptRef = LegacyXmlSerialiser.getDocRefFromLegacyXml(oldVisualisation.getScriptRefXML());
                document.setScriptRef(MappingUtil.map(scriptRef));

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
