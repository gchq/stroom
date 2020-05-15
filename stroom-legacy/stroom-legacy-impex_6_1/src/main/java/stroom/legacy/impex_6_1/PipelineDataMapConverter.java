package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.PipelineEntity;
import stroom.pipeline.PipelineSerialiser;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
class PipelineDataMapConverter implements DataMapConverter {
    private final PipelineSerialiser serialiser;

    @Inject
    PipelineDataMapConverter(final PipelineSerialiser serialiser) {
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
                PipelineEntity oldPipeline = new PipelineEntity();
                LegacyXmlSerialiser.performImport(oldPipeline, dataMap);

                final PipelineDoc document = new PipelineDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldPipeline.getCreateTime());
                document.setUpdateTimeMs(oldPipeline.getUpdateTime());
                document.setCreateUser(oldPipeline.getCreateUser());
                document.setUpdateUser(oldPipeline.getUpdateUser());

                document.setDescription(oldPipeline.getDescription());

                final stroom.legacy.model_6_1.DocRef pipelineRef = LegacyXmlSerialiser.getDocRefFromLegacyXml(oldPipeline.getParentPipelineXML());
                document.setParentPipeline(MappingUtil.map(pipelineRef));

                final stroom.legacy.model_6_1.PipelineData pipelineData = LegacyXmlSerialiser.getPipelineDataFromLegacyXml(oldPipeline.getData());
                document.setPipelineData(MappingUtil.map(pipelineData));

//                if (dataMap.containsKey("data.xml")) {
//                    final PipelineData pd = getPipelineDataFromXml(EncodingUtil.asString(dataMap.remove("data.xml")));
//                    document.setPipelineData(pd);
//                }

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
