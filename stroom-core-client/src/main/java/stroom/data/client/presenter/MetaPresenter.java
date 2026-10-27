/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.component.ExpressionDialogController;
import stroom.data.client.component.MetaListController;
import stroom.data.client.component.MetaRelationListController;
import stroom.data.client.presenter.MetaPresenter.MetaView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummaryRequest;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.shared.ExpressionResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Set;
import java.util.function.Consumer;

public class MetaPresenter
        extends MyPresenterWidget<MetaView>
        implements HasDocumentRead<Object>,
        BeginSteppingHandler {

    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ExpressionValidator expressionValidator;
    private final MetaListController metaListPresenter;
    private final MetaRelationListController metaRelationListPresenter;
    private final DataPresenter dataPresenter;
    private final Provider<DataUploadPresenter> streamUploadPresenter;
    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final ButtonView streamListFilter;
    private final ButtonView streamListInfo;
    private final ClientSecurityContext securityContext;

    private DocRef feedRef;
    private ButtonView streamListUpload;
    private ButtonView streamListDownload;
    private ButtonView streamListDelete;
    private ButtonView streamListRestore;
    private ButtonView streamListProcess;
    private ButtonView streamRelationListDownload;
    private ButtonView streamRelationListDelete;
    private ButtonView streamRelationListRestore;
    private ButtonView streamRelationListProcess;

    private boolean hasSetCriteria;

    @Inject
    public MetaPresenter(final EventBus eventBus,
                         final MetaView view,
                         final MetaListController metaListPresenter,
                         final MetaRelationListController metaRelationListPresenter,
                         final DataPresenter dataPresenter,
                         final Provider<ExpressionDialogController> filterDialogProvider,
                         final Provider<DataUploadPresenter> streamUploadPresenter,
                         final ClientSecurityContext securityContext,
                         final RestFactory restFactory,
                         final DateTimeSettingsFactory dateTimeSettingsFactory,
                         final ExpressionValidator expressionValidator) {
        super(eventBus, view);
        own(metaListPresenter);
        own(metaRelationListPresenter);
        own(dataPresenter);
        this.metaListPresenter = metaListPresenter;
        this.metaRelationListPresenter = metaRelationListPresenter;
        this.filterDialogProvider = filterDialogProvider;
        this.streamUploadPresenter = streamUploadPresenter;
        this.dataPresenter = dataPresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.expressionValidator = expressionValidator;
        this.securityContext = securityContext;

        getView().setStreamListView(metaListPresenter);
        getView().setStreamRelationListView(metaRelationListPresenter);
        getView().setDataView(dataPresenter.getView());

        dataPresenter.setBeginSteppingHandler(this);

        metaListPresenter.getToolbar().setName("Stream actions");
        metaRelationListPresenter.getToolbar().setName("Related stream actions");

        // Process
        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            streamListProcess = metaListPresenter.getToolbar().button(SvgImage.PROCESS, "Process Selected Streams",
                    () -> metaListPresenter.process());
            streamListProcess.setEnabled(false);
            streamRelationListProcess = metaRelationListPresenter.getToolbar().button(SvgImage.PROCESS,
                    "Process Selected Related Streams",
                    () -> metaRelationListPresenter.process());
            streamRelationListProcess.setEnabled(false);
        }

        // Delete, Undelete, DE-duplicate
        if (securityContext.hasAppPermission(AppPermission.DELETE_DATA_PERMISSION)) {
            streamListDelete = metaListPresenter.getToolbar().button(SvgImage.DELETE, "Delete Selected Streams",
                    () -> metaListPresenter.delete());
            streamListDelete.setEnabled(false);
            streamRelationListDelete = metaRelationListPresenter.getToolbar().button(SvgImage.DELETE,
                    "Delete Selected Related Streams",
                    () -> metaRelationListPresenter.delete());
            streamRelationListDelete.setEnabled(false);
            streamListRestore = metaListPresenter.getToolbar().button(SvgImage.UNDO, "Restore Selected Streams",
                    () -> metaListPresenter.restore());
            streamListRestore.setEnabled(false);
            streamRelationListRestore = metaRelationListPresenter.getToolbar().button(SvgImage.UNDO,
                    "Restore Selected Related Streams",
                    () -> metaRelationListPresenter.restore());
            streamRelationListRestore.setEnabled(false);
        }

        // Selection information
        streamListInfo = metaListPresenter.getToolbar().button(SvgImage.INFO,
                "Show Summary of Selected Streams",
                () -> metaListPresenter.info());

        // Download
        if (securityContext.hasAppPermission(AppPermission.EXPORT_DATA_PERMISSION)) {
            streamListDownload = metaListPresenter.getToolbar().button(SvgImage.DOWNLOAD,
                    "Download Selected Streams",
                    () -> metaListPresenter.download());
            streamListDownload.setEnabled(false);
            streamRelationListDownload = metaRelationListPresenter.getToolbar().button(SvgImage.DOWNLOAD,
                    "Download Selected Related Streams",
                    () -> metaRelationListPresenter.download());
            streamRelationListDownload.setEnabled(false);
        }

        // Upload
        if (securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            streamListUpload = metaListPresenter.getToolbar().button(SvgImage.UPLOAD, "Upload Data to Feed",
                    () -> make(streamUploadPresenter).show(MetaPresenter.this, feedRef));
        }

        // Filter
        streamListFilter = metaListPresenter.getToolbar().button(SvgImage.FILTER, "Filter Streams",
                this::onStreamListFilter);

        // Init the buttons
        setStreamListSelectableEnabled(null);
        setStreamRelationListSelectableEnabled(null);

        own(metaListPresenter.getSelectionModel().addSelectionHandler(event -> {
            metaRelationListPresenter.setSelectedStream(metaListPresenter.getSelected(), true,
                    !Status.UNLOCKED.equals(MetaExpressionUtil.getSingleStatus(getCriteria())));
            // showData() gets called by the metaRelationListPresenter selection handler
        }));
        own(metaListPresenter.addSelectionListener(selected ->
                setStreamListSelectableEnabled(selected)));
        own(metaRelationListPresenter.getSelectionModel().addSelectionHandler(event ->
                showData()));
        own(metaRelationListPresenter.addSelectionListener(selected ->
                setStreamRelationListSelectableEnabled(selected)));
    }

    private static Meta getMeta(final MetaListController streamListPresenter, final long id) {
        final ResultPage<MetaRow> list = streamListPresenter.getResultPage();
        if (list != null) {
            if (list.getValues() != null) {
                for (final MetaRow metaRow : list.getValues()) {
                    if (metaRow.getMeta().getId() == id) {
                        return metaRow.getMeta();
                    }
                }
            }
        }
        return null;
    }

    private void onStreamListFilter() {
        filterDialogProvider.get().show(this,
                "Filter Streams",
                PopupSize.resizable(1_000, 600),
                getCriteria().getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                MetaFields.getAllFields(),
                (expression, e) -> {
                    expressionValidator.validateExpression(
                            MetaPresenter.this,
                            MetaFields.getAllFields(),
                            expression, expression2 -> {
                                if (!expression2.equals(getCriteria().getExpression())) {
                                    if (MetaExpressionUtil.hasAdvancedCriteria(expression2)) {
                                        ConfirmEvent.fire(MetaPresenter.this,
                                                "You are setting advanced filters!  It is recommended you constrain " +
                                                "your filter (e.g. by 'Created') to avoid an expensive query.  "
                                                + "Are you sure you want to apply this advanced filter?",
                                                confirm -> {
                                                    if (confirm) {
                                                        setExpression(expression2);
                                                        e.hide();
                                                    } else {
                                                        // Don't hide
                                                        e.reset();
                                                    }
                                                });
                                    } else {
                                        setExpression(expression2);
                                        e.hide();
                                    }
                                } else {
                                    // Nothing changed!
                                    e.hide();
                                }
                            },
                            // If it isn't valid, let the user change it or cancel
                            e::reset,
                            this);
                });
    }

    private void setExpression(final ExpressionOperator expression) {
        // Copy new filter settings back.
        getCriteria().setExpression(expression);
        // Reset the page offset.
        getCriteria().obtainPageRequest().setOffset(0);

        // Init the buttons
        setStreamListSelectableEnabled(metaListPresenter.getSelection());

        // Clear the current selection and get a new list of streams.
        metaListPresenter.getSelectionModel().clear();
        metaListPresenter.refresh();
    }


    private void showData() {
        final Meta meta = getSelected();
        if (meta == null) {
            dataPresenter.clear();
        } else {
            dataPresenter.fetchData(meta);
        }
    }

    public void refresh() {
//        GWT.log("Refresh");
        // Get a new list of streams.
        metaListPresenter.refresh();
        metaRelationListPresenter.refresh();
    }

    public void refreshData() {
        if (dataPresenter != null) {
            dataPresenter.update(false);
        }
    }

    public FindMetaCriteria getCriteria() {
        return metaListPresenter.getCriteria();
    }

    @Override
    public void read(final DocRef docRef, final Object document) {
        if (document instanceof FeedDoc) {
            setFeedCriteria(docRef);
        } else if (document instanceof PipelineDoc) {
            setPipelineCriteria(docRef);
        } else if (docRef != null) {
            setFolderCriteria(docRef);
        } else {
            setNullCriteria();
        }
    }

    private void setFolderCriteria(final DocRef folder) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            showUploadButton(false);

            if (ExplorerConstants.SYSTEM_TYPE.equals(folder.getType())) {
                // No point in adding a term for the root folder as everything is a descendent of it
                metaListPresenter.setSeedExpression(
                        ExpressionValidator.ALL_UNLOCKED_EXPRESSION,
                        this::refresh);
            } else {
                metaListPresenter.setSeedExpression(
                        MetaExpressionUtil.createFolderExpression(folder),
                        this::refresh);
            }
        }
    }

    private void setFeedCriteria(final DocRef feedRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            this.feedRef = feedRef;
            showUploadButton(true);
            metaListPresenter.setSeedExpression(
                    MetaExpressionUtil.createFeedExpression(feedRef),
                    this::refresh);
        }
    }

    private void setPipelineCriteria(final DocRef pipelineRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            showUploadButton(false);
            metaListPresenter.setSeedExpression(
                    MetaExpressionUtil.createPipelineExpression(pipelineRef),
                    this::refresh);
        }
    }

    private void setNullCriteria() {
        showUploadButton(false);
        metaListPresenter.setSeedExpression(
                ExpressionValidator.ALL_UNLOCKED_EXPRESSION,
                this::refresh);
    }

    private Meta getSelected() {
        MetaRow selected = metaListPresenter.getSelected();
        if (metaRelationListPresenter.getSelected() != null) {
            selected = metaRelationListPresenter.getSelected();
        }

        if (selected != null) {
            return selected.getMeta();
        }

        return null;
    }

    /// Listens for the user selecting streams in the list, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<Selection<Long>> listener) {
        return metaListPresenter.addSelectionListener(listener);
    }

    private void showUploadButton(final boolean visible) {
        if (streamListUpload != null) {
            streamListUpload.setVisible(visible);
        }
    }

    public boolean isSomeSelected(final MetaListController streamListPresenter,
                                  final Selection<Long> selectedIdSet) {
        if (streamListPresenter.getResultPage() == null || streamListPresenter.getResultPage().isEmpty()) {
            return false;
        }
        return selectedIdSet != null && !selectedIdSet.isMatchNothing();
    }

    public void setStreamListSelectableEnabled(final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);

        ButtonView.setEnabled(streamListInfo, someSelected);
        ButtonView.setEnabled(streamListDownload, someSelected);
        ButtonView.setEnabled(streamListProcess, someSelected);
        if (streamListDelete != null) {
            shouldEnableDelete(metaListPresenter, streamIdSet, streamListDelete::setEnabled);
        }
        if (streamListRestore != null) {
            shouldEnableRestore(metaListPresenter, streamIdSet, streamListRestore::setEnabled);
        }
    }

    private void setStreamRelationListSelectableEnabled(final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaRelationListPresenter, streamIdSet);

        ButtonView.setEnabled(streamRelationListDownload, someSelected);
        ButtonView.setEnabled(streamRelationListProcess, someSelected);
        if (streamRelationListDelete != null) {
            shouldEnableDelete(metaRelationListPresenter, streamIdSet, streamRelationListDelete::setEnabled);
        }
        if (streamRelationListRestore != null) {
            shouldEnableRestore(metaRelationListPresenter, streamIdSet, streamRelationListRestore::setEnabled);
        }
    }

    private void shouldEnableDelete(final MetaListController metaListPresenter,
                                    final Selection<Long> streamIdSet,
                                    final Consumer<Boolean> isEnabledConsumer) {
//        if (streamIdSet != null) {
//            GWT.log("streamIdSet (" + streamIdSet.size() + ") - " + streamIdSet);
//        }
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = MetaExpressionUtil.getStatusSet(getCriteria().getExpression());
            final boolean allowDelete = statusSet.isEmpty() ||
                                        statusSet.contains(Status.LOCKED) ||
                                        statusSet.contains(Status.UNLOCKED);

            boolean isDeleteEnabled = false;
            if (allowDelete) {
                if (streamIdSet != null) {
                    if (streamIdSet.isMatchAll()) {
//                        return true;
                        isDeleteEnabled = true;
                    } else {
                        for (final Long id : streamIdSet.getSet()) {
                            final Meta meta = getMeta(metaListPresenter, id);
                            if (meta != null
                                && !Status.DELETED.equals(meta.getStatus())) {
//                                return true;
                                isDeleteEnabled = true;
                                break;
                            }
                        }
                    }
                }
                if (isDeleteEnabled) {
                    // Check how many of the selected items the user actually has DELETE perms on.
                    // If none, don't enable, if >0 do enable
                    final FindMetaCriteria selectedCriteria = metaListPresenter.getSelectedCriteria();
                    restFactory
                            .create(META_RESOURCE)
                            .method(res ->
                                    res.getSelectionSummary(new SelectionSummaryRequest(
                                            selectedCriteria,
                                            DocumentPermission.DELETE)))
                            .onSuccess(selectionSummary -> {
                                isEnabledConsumer.accept(selectionSummary.getItemCount() > 0);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                } else {
                    isEnabledConsumer.accept(false);
                }
            }
        }
        isEnabledConsumer.accept(false);
    }

    private void shouldEnableRestore(final MetaListController metaListPresenter,
                                     final Selection<Long> streamIdSet,
                                     final Consumer<Boolean> isEnabledConsumer) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = MetaExpressionUtil.getStatusSet(getCriteria().getExpression());
            final boolean allowRestore = statusSet.isEmpty() ||
                                         statusSet.contains(Status.DELETED);

            boolean isRestoreEnabled = false;
            if (allowRestore) {
                if (streamIdSet != null) {
                    if (streamIdSet.isMatchAll()) {
                        isRestoreEnabled = true;
                    } else {
                        for (final Long id : streamIdSet.getSet()) {
                            final Meta meta = getMeta(metaListPresenter, id);
                            if (meta != null && Status.DELETED.equals(meta.getStatus())) {
                                isRestoreEnabled = true;
                                break;
                            }
                        }
                    }
                }

                if (isRestoreEnabled) {
                    // Check how many of the selected items the user actually has UPDATE perms on.
                    // If none, don't enable, if >0 do enable
                    final FindMetaCriteria selectedCriteria = metaListPresenter.getSelectedCriteria();
                    restFactory
                            .create(META_RESOURCE)
                            .method(res ->
                                    res.getSelectionSummary(new SelectionSummaryRequest(
                                            selectedCriteria,
                                            DocumentPermission.EDIT)))
                            .onSuccess(selectionSummary -> {
                                isEnabledConsumer.accept(selectionSummary.getItemCount() > 0);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                } else {
                    isEnabledConsumer.accept(false);
                }
            }
        }
        isEnabledConsumer.accept(false);
    }

    @Override
    public void beginStepping(final StepType stepType,
                              final StepLocation stepLocation,
                              final String childStreamType) {
        // Try and get a pipeline id to use as a starting point for
        // stepping.
        final DocRef pipelineRef = null;

        // We will assume that the stream list has a child stream selected.
        // This would be the case where a user chooses an event with errors
        // in the top screen and then chooses the raw stream in the middle
        // pane to step through.
        Long childStreamId = null;
        final MetaRow map = metaListPresenter.getSelected();
        if (map != null && map.getMeta() != null) {
            final Meta childMeta = map.getMeta();
            // If the top list has a raw stream selected or isn't a child of
            // the selected stream then this isn't the child stream we are
            // looking for.
            if (childMeta.getParentMetaId() != null && childMeta.getParentMetaId().equals(stepLocation.getMetaId())) {
                childStreamId = childMeta.getId();
            }
        }

        BeginPipelineSteppingEvent.fire(
                this,
                childStreamId,
                childStreamType,
                stepType,
                stepLocation,
                pipelineRef);
    }


    // --------------------------------------------------------------------------------


    public interface MetaView extends View {

        /// Shows the list of streams.
        ///
        /// @param view Its view.
        void setStreamListView(IsWidget view);

        /// Shows the streams related to the selected one.
        ///
        /// @param view Its view.
        void setStreamRelationListView(IsWidget view);

        /// Shows the selected stream's data.
        ///
        /// @param view Its view.
        void setDataView(View view);
    }
}
