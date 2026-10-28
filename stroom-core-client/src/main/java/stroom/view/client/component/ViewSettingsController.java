/*
 * Copyright 2022 Crown Copyright
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

package stroom.view.client.component;

import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.util.shared.NullSafe;
import stroom.view.shared.ViewDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A view's Settings tab ([ViewSettings]): the data source it queries, the pipeline that extracts
/// its fields (filtered by the tags Stroom is set to), and the filter on the streams analytic rules
/// process.
public class ViewSettingsController extends Controller {

    private final DocSelectionController dataSource;
    private final DocSelectionController pipeline;
    private final ExpressionEditorController expressionEditor;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the data source and pipeline pickers.
    /// @param expressionEditor     The filter on the streams analytic rules process.
    /// @param uiConfigCache        Gives the tags the pipeline picker shows.
    @Inject
    public ViewSettingsController(final EventBus eventBus,
                                  final ViewSettings view,
                                  final Provider<DocSelectionController> docSelectionProvider,
                                  final ExpressionEditorController expressionEditor,
                                  final UiConfigCache uiConfigCache) {
        super(eventBus);
        initWidget(view);
        own(expressionEditor);
        this.expressionEditor = expressionEditor;

        dataSource = make(docSelectionProvider);
        dataSource.attach(view.getDataSourceBox());
        dataSource.setNodeFlags(NodeFlag.DATA_SOURCE);
        dataSource.setRequiredPermissions(DocumentPermission.USE);
        dataSource.setItemType("Data Source");

        pipeline = make(docSelectionProvider);
        pipeline.attach(view.getPipelineBox());
        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.USE);

        view.setExpression(expressionEditor);

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(guard(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                NullSafe.consume(
                        extendedUiConfig.getQuery(),
                        QueryConfig::getViewPipelineSelectorIncludedTags,
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipeline::setQuickFilter);
            }
        }), taskMonitor());

        own(dataSource.addSelectionListener(docRef -> markChanged()));
        own(pipeline.addSelectionListener(docRef -> markChanged()));
        expressionEditor.setOnChange(this::markChanged);

        watch(Contexts.READ_ONLY, readOnly -> {
            dataSource.setReadOnly(readOnly);
            pipeline.setReadOnly(readOnly);
            expressionEditor.setReadOnly(readOnly);
        });
    }


    /// Shows a view's data source, pipeline and stream filter.
    ///
    /// @param docRef The view.
    /// @param entity The view.
    public void read(final DocRef docRef, final ViewDoc entity) {
        dataSource.setSelectedEntityReference(entity.getDataSource(), true);
        pipeline.setSelectedEntityReference(entity.getPipeline(), true);
        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(MetaFields.getAllFields());
        expressionEditor.init(MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);

        // Read expression.
        ExpressionOperator root = entity.getFilter();
        if (root == null) {
            root = ExpressionOperator.builder().build();
        }
        expressionEditor.read(root);
    }

    /// @param entity The view.
    /// @return A copy of it with the data source, pipeline and stream filter shown.
    public ViewDoc write(final ViewDoc entity) {
        return entity
                .copy()
                .dataSource(dataSource.getSelectedEntityReference())
                .pipeline(pipeline.getSelectedEntityReference())
                .filter(expressionEditor.write())
                .build();
    }
}
