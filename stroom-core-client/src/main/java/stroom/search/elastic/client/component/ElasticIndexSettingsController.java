/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.elastic.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.UUID;

/// An Elasticsearch index's Settings tab ([ElasticIndexSettings]), with its cluster's, pipeline's
/// and models' pickers, which tests the connection to the index when asked.
public class ElasticIndexSettingsController extends Controller {

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    private final ElasticIndexSettings view;
    private final DocSelectionController cluster;
    private final DocSelectionController vectorGenerationModel;
    private final DocSelectionController rerankModel;
    private final DocSelectionController pipeline;
    private final RestFactory restFactory;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the pickers' controllers.
    /// @param restFactory          Tests the connection.
    @Inject
    public ElasticIndexSettingsController(final EventBus eventBus,
                                          final ElasticIndexSettings view,
                                          final Provider<DocSelectionController> docSelectionProvider,
                                          final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);

        cluster = make(docSelectionProvider);
        cluster.attach(view.getClusterBox());
        cluster.setIncludedTypes(ElasticClusterDoc.TYPE);
        cluster.setRequiredPermissions(DocumentPermission.USE);
        vectorGenerationModel = make(docSelectionProvider);
        vectorGenerationModel.attach(view.getVectorGenerationModelBox());
        vectorGenerationModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        vectorGenerationModel.setRequiredPermissions(DocumentPermission.USE);
        rerankModel = make(docSelectionProvider);
        rerankModel.attach(view.getRerankModelBox());
        rerankModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModel.setRequiredPermissions(DocumentPermission.USE);
        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getDefaultExtractionPipelineBox());
        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.VIEW);

        view.setOnChange(this::markChanged);
        view.setOnTestConnection(this::testIndex);
        own(cluster.addSelectionListener(event -> markChanged()));
        own(vectorGenerationModel.addSelectionListener(event -> markChanged()));
        own(rerankModel.addSelectionListener(event -> markChanged()));
        own(pipeline.addSelectionListener(selection -> markChanged()));
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows an index's settings.
    ///
    /// @param docRef The index.
    /// @param index  Its settings.
    public void read(final DocRef docRef, final ElasticIndexDoc index) {
        cluster.setSelectedEntityReference(index.getClusterRef(), true);
        vectorGenerationModel.setSelectedEntityReference(index.getVectorGenerationModelRef(), true);
        rerankModel.setSelectedEntityReference(index.getRerankModelRef(), true);
        view.read(index);
        pipeline.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
    }

    /// Writes the settings shown into a copy of an index.
    ///
    /// @param index The index.
    /// @return A copy of it with the settings shown.
    public ElasticIndexDoc write(final ElasticIndexDoc index) {
        return view.write(index.copy())
                .clusterRef(cluster.getSelectedEntityReference())
                .vectorGenerationModelRef(vectorGenerationModel.getSelectedEntityReference())
                .rerankModelRef(rerankModel.getSelectedEntityReference())
                .defaultExtractionPipeline(pipeline.getSelectedEntityReference())
                .build();
    }

    private void testIndex() {
        final ElasticIndexDoc index = write(ElasticIndexDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .build());
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(res -> res.testIndex(index))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .owner(this)
                .exec();
    }

    private void setReadOnly(final boolean readOnly) {
        view.setReadOnly(readOnly);
        cluster.setReadOnly(readOnly);
        vectorGenerationModel.setReadOnly(readOnly);
        rerankModel.setReadOnly(readOnly);
        pipeline.setReadOnly(readOnly);
    }
}
