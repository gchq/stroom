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

package stroom.search.solr.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.DynamicFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.UUID;

/// A Solr index's Settings tab ([SolrIndexSettings]): how to reach Solr, a test of the connection
/// with it, the default extraction pipeline and the data retention expression. Read only as its
/// document is.
public class SolrIndexSettingsController extends Controller {

    private static final SolrIndexResource SOLR_INDEX_RESOURCE = GWT.create(SolrIndexResource.class);

    private final SolrIndexSettings view;
    private final ExpressionEditorController expressionEditor;
    private final DocSelectionController pipeline;
    private final RestFactory restFactory;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The tab.
    /// @param expressionEditor       Edits the data retention expression.
    /// @param docSelectionProvider   Makes the default extraction pipeline picker's controller.
    /// @param restFactory            Tests the connection.
    /// @param fieldSelectionBoxModel The index's fields, for the expression.
    @Inject
    public SolrIndexSettingsController(final EventBus eventBus,
                                       final SolrIndexSettings view,
                                       final ExpressionEditorController expressionEditor,
                                       final Provider<DocSelectionController> docSelectionProvider,
                                       final RestFactory restFactory,
                                       final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus);
        this.view = view;
        this.expressionEditor = own(expressionEditor).attach(view.getRetentionExpressionEditor());
        this.restFactory = restFactory;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;
        initWidget(view);
        // Shows its requests' progress as this tab does (its context's)
        fieldSelectionBoxModel.setTaskMonitorFactory(taskMonitor());

        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getDefaultExtractionPipelineBox());
        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.VIEW);

        view.setOnChange(this::markChanged);
        view.setOnTestConnection(this::onTestConnection);

        expressionEditor.setOnChange(this::markChanged);
        own(pipeline.addSelectionListener(selection -> markChanged()));
        // Read only as its document is
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onTestConnection() {
        view.setTestingConnection(true);
        final SolrIndexDoc index = write(SolrIndexDoc.builder().uuid(UUID.randomUUID().toString()).build());
        restFactory
                .create(SOLR_INDEX_RESOURCE)
                .method(res -> res.solrConnectionTest(index))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), () ->
                                view.setTestingConnection(false));
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), () ->
                                view.setTestingConnection(false));
                    }
                })
                .onFailure(new DefaultErrorHandler(this, () -> view.setTestingConnection(false)))
                .owner(this)
                .taskMonitorFactory(view.getTestConnectionTaskMonitor())
                .exec();
    }

    /// Shows a Solr index's settings.
    ///
    /// @param docRef The index.
    /// @param index  The index.
    public void read(final DocRef docRef, final SolrIndexDoc index) {
        final SolrConnectionConfig connectionConfig = index.getSolrConnectionConfig();
        if (connectionConfig != null) {
            view.setInstanceType(connectionConfig.getInstanceType());
            view.setSolrUrls(connectionConfig.getSolrUrls());
            view.setZkHosts(connectionConfig.getZkHosts());
            view.setZkPath(connectionConfig.getZkPath());
            view.setUseZk(connectionConfig.isUseZk());
        }

        view.setCollection(index.getCollection());
        view.setTimeField(index.getTimeField());

        ExpressionOperator retentionExpression = index.getRetentionExpression();
        if (retentionExpression == null) {
            retentionExpression = ExpressionOperator.builder().build();
        }

        final boolean readOnly = context(Contexts.READ_ONLY);
        fieldSelectionBoxModel.setDataSourceRefConsumer(consumer -> consumer.accept(docRef));
        expressionEditor.init(docRef, fieldSelectionBoxModel);
        expressionEditor.read(retentionExpression);
        expressionEditor.setReadOnly(readOnly);
        pipeline.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
        pipeline.setReadOnly(readOnly);
    }

    /// Writes the settings shown into a copy of a Solr index.
    ///
    /// @param index The index.
    /// @return A copy of it with the settings shown.
    public SolrIndexDoc write(final SolrIndexDoc index) {
        final SolrConnectionConfig connectionConfig = SolrConnectionConfig
                .builder()
                .instanceType(view.getInstanceType())
                .solrUrls(view.getSolrUrls())
                .zkHosts(view.getZkHosts())
                .zkPath(view.getZkPath())
                .useZk(view.isUseZk())
                .build();

        String collection = null;
        if (!NullSafe.isBlankString(view.getCollection())) {
            collection = view.getCollection().trim();
        }

        return index
                .copy()
                .collection(collection)
                .timeField(view.getTimeField())
                .solrConnectionConfig(connectionConfig)
                .retentionExpression(expressionEditor.write())
                .defaultExtractionPipeline(pipeline.getSelectedEntityReference())
                .build();
    }

}
