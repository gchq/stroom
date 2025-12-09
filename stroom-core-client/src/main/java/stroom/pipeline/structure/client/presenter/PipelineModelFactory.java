package stroom.pipeline.structure.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PipelineModelFactory {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);

    private final RestFactory restFactory;

    private PipelineModel pipelineModel;

    @Inject
    public PipelineModelFactory(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }


    public void get(final TaskMonitorFactory taskMonitorFactory, final DocRef docRef,
                    final PipelineElementTypes elementTypes, final Consumer<PipelineModel> consumer) {

        if (pipelineModel != null) {
            consumer.accept(pipelineModel);
        } else {
            restFactory
                    .create(PIPELINE_RESOURCE)
                    .method(res -> res.fetchPipelineLayers(docRef))
                    .onSuccess(result -> {
                        final PipelineLayer pipelineLayer = result.get(result.size() - 1);
                        final List<PipelineLayer> baseStack = new ArrayList<>(result.size() - 1);

                        // If there is a stack of pipeline data then we need
                        // to make sure changes are reflected appropriately.
                        for (int i = 0; i < result.size() - 1; i++) {
                            baseStack.add(result.get(i));
                        }

                        pipelineModel = new PipelineModel(elementTypes);
                        pipelineModel.setPipelineLayer(pipelineLayer);
                        pipelineModel.setBaseStack(baseStack);
                        pipelineModel.build();

                        consumer.accept(pipelineModel);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();

        }
    }
}
