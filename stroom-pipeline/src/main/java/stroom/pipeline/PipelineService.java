package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;


import java.util.List;

public interface PipelineService {

    PipelineDoc fetch(final String uuid);

    PipelineDoc update(final String uuid, final PipelineDoc doc);

    Boolean savePipelineXml(final DocRef pipeline, String xml);

    String fetchPipelineXml(final DocRef pipeline);

    List<PipelineData> fetchPipelineData(final DocRef pipeline);
}