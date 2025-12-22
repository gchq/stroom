/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
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
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.shared.ExpressionResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class MetaPresenter
        extends MyPresenterWidget<MetaView>
        implements HasDataSelectionHandlers<Selection<Long>>,
        HasDocumentRead<Object>,
        BeginSteppingHandler {

    public static final String DATA = "DATA";
    public static final String STREAM_RELATION_LIST = "STREAM_RELATION_LIST";
    public static final String STREAM_LIST = "STREAM_LIST";

    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ExpressionValidator expressionValidator;
    private final MetaListPresenter metaListPresenter;
    private final MetaRelationListPresenter metaRelationListPresenter;
    private final DataPresenter dataPresenter;
    private final Provider<DataUploadPresenter> streamUploadPresenter;
    private final Provider<ExpressionPresenter> streamListFilterPresenter;
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
                         final MetaListPresenter metaListPresenter,
                         final MetaRelationListPresenter metaRelationListPresenter,
                         final DataPresenter dataPresenter,
                         final Provider<ExpressionPresenter> streamListFilterPresenter,
                         final Provider<DataUploadPresenter> streamUploadPresenter,
                         final ClientSecurityContext securityContext,
                         final RestFactory restFactory,
                         final DateTimeSettingsFactory dateTimeSettingsFactory,
                         final ExpressionValidator expressionValidator) {
        super(eventBus, view);
        this.metaListPresenter = metaListPresenter;
        this.metaRelationListPresenter = metaRelationListPresenter;
        this.streamListFilterPresenter = streamListFilterPresenter;
        this.streamUploadPresenter = streamUploadPresenter;
        this.dataPresenter = dataPresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.expressionValidator = expressionValidator;
        this.securityContext = securityContext;

        setInSlot(STREAM_LIST, metaListPresenter);
        setInSlot(STREAM_RELATION_LIST, metaRelationListPresenter);
        setInSlot(DATA, dataPresenter);

        dataPresenter.setBeginSteppingHandler(this);

        // Process
        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            streamListProcess = metaListPresenter.add(SvgPresets.PROCESS);
            streamRelationListProcess = metaRelationListPresenter.add(SvgPresets.PROCESS);
        }

        // Delete, Undelete, DE-duplicate
        if (securityContext.hasAppPermission(AppPermission.DELETE_DATA_PERMISSION)) {
            streamListDelete = metaListPresenter.add(SvgPresets.DELETE);
            streamListDelete.setEnabled(false);
            streamRelationListDelete = metaRelationListPresenter.add(SvgPresets.DELETE);
            streamRelationListDelete.setEnabled(false);
            streamListRestore = metaListPresenter.add(SvgPresets.UNDO);
            streamListRestore.setTitle("Restore");
            streamRelationListRestore = metaRelationListPresenter.add(SvgPresets.UNDO);
            streamRelationListRestore.setTitle("Restore");
        }

        // Selection information
        streamListInfo = metaListPresenter.add(SvgPresets.INFO.title("Selection summary"));

        // Download
        if (securityContext.hasAppPermission(AppPermission.EXPORT_DATA_PERMISSION)) {
            streamListDownload = metaListPresenter.add(SvgPresets.DOWNLOAD);
            streamRelationListDownload = metaRelationListPresenter.add(SvgPresets.DOWNLOAD);
        }

        // Upload
        if (securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            streamListUpload = metaListPresenter.add(SvgPresets.UPLOAD);
        }

        // Filter
        streamListFilter = metaListPresenter.add(SvgPresets.FILTER);

        // Init the buttons
        setStreamListSelectableEnabled(null);
        setStreamRelationListSelectableEnabled(null);
    }

    private static Meta getMeta(final AbstractMetaListPresenter streamListPresenter, final long id) {
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

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(metaListPresenter.getSelectionModel().addSelectionHandler(event -> {
            metaRelationListPresenter.setSelectedStream(metaListPresenter.getSelected(), true,
                    !Status.UNLOCKED.equals(getSingleStatus(getCriteria())));
            // showData() gets called by the metaRelationListPresenter selection handler
        }));
        registerHandler(metaListPresenter.addDataSelectionHandler(event ->
                setStreamListSelectableEnabled(event.getSelectedItem())));
        registerHandler(metaRelationListPresenter.getSelectionModel().addSelectionHandler(event ->
                showData()));
        registerHandler(metaRelationListPresenter.addDataSelectionHandler(event ->
                setStreamRelationListSelectableEnabled(event.getSelectedItem())));

        registerHandler(streamListFilter.addClickHandler(event -> {
            final ExpressionPresenter presenter = streamListFilterPresenter.get();

            final HidePopupRequestEvent.Handler HidePopupRequestEventHandler = e -> {
                if (e.isOk()) {
                    final ExpressionOperator expression = presenter.write();

                    expressionValidator.validateExpression(
                            MetaPresenter.this,
                            MetaFields.getAllFields(),
                            expression, expression2 -> {
                                if (!expression2.equals(getCriteria().getExpression())) {
                                    if (hasAdvancedCriteria(expression2)) {
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
                            }, this);
                } else {
                    e.hide();
                }
            };

            presenter.read(getCriteria().getExpression(),
                    MetaFields.STREAM_STORE_DOC_REF,
                    MetaFields.getAllFields());

            presenter.getWidget().getElement().addClassName("default-min-sizes");
            final PopupSize popupSize = PopupSize.resizable(1_000, 600);
            ShowPopupEvent.builder(presenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Filter Streams")
                    .onShow(e -> presenter.focus())
                    .onHideRequest(HidePopupRequestEventHandler)
                    .fire();
        }));

        // Some button's may not exist due to permissions
        if (streamListUpload != null) {
            registerHandler(streamListUpload.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    streamUploadPresenter.get().show(MetaPresenter.this, feedRef);
                }
            }));
        }
        if (streamListInfo != null) {
            registerHandler(streamListInfo.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaListPresenter.info();
                }
            }));
        }
        if (streamListDownload != null) {
            registerHandler(streamListDownload.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaListPresenter.download();
                }
            }));
        }
        if (streamRelationListDownload != null) {
            registerHandler(streamRelationListDownload.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaRelationListPresenter.download();
                }
            }));
        }
        // Delete
        if (streamListDelete != null) {
            registerHandler(streamListDelete.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaListPresenter.delete();
                }
            }));
        }
        if (streamRelationListDelete != null) {
            registerHandler(streamRelationListDelete.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaRelationListPresenter.delete();
                }
            }));
        }
        // Restore
        if (streamListRestore != null) {
            registerHandler(streamListRestore.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaListPresenter.restore();
                }
            }));
        }
        if (streamRelationListRestore != null) {
            registerHandler(streamRelationListRestore.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaRelationListPresenter.restore();
                }
            }));
        }
        // Process
        if (streamListProcess != null) {
            registerHandler(streamListProcess.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaListPresenter.process();
                }
            }));
        }
        if (streamRelationListProcess != null) {
            registerHandler(streamRelationListProcess.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    metaRelationListPresenter.process();
                }
            }));
        }
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

    public boolean hasAdvancedCriteria(final ExpressionOperator expression) {
        final Status status = getSingleStatus(expression);

        if (!Status.UNLOCKED.equals(status)) {
            return true;
        }

        final Set<String> statusPeriod = getTerms(expression, MetaFields.STATUS_TIME);
        return !statusPeriod.isEmpty();
    }

    private static Status getSingleStatus(final FindMetaCriteria criteria) {
        if (criteria == null) {
            return null;
        }
        return getSingleStatus(criteria.getExpression());
    }

    private static Status getSingleStatus(final ExpressionOperator expression) {
        final Set<Status> streamStatuses = getStatusSet(expression);
        if (streamStatuses.size() == 1) {
            return streamStatuses.iterator().next();
        }
        return null;
    }

    private static Set<Status> getStatusSet(final ExpressionOperator expression) {
        final Set<String> terms = getTerms(expression, MetaFields.STATUS);
        final Set<Status> streamStatuses = new HashSet<>();
        for (final String term : terms) {
            for (final Status streamStatus : Status.values()) {
                if (streamStatus.getDisplayValue().equals(term)) {
                    streamStatuses.add(streamStatus);
                }
            }
        }

        return streamStatuses;
    }

    private static Set<String> getTerms(final ExpressionOperator expression, final QueryField field) {
        final Set<String> terms = new HashSet<>();
        if (expression != null) {
            getTerms(expression, field, terms);
        }
        return terms;
    }

    private static void getTerms(final ExpressionOperator expressionOperator,
                                 final QueryField field,
                                 final Set<String> terms) {
        if (expressionOperator.enabled() && expressionOperator.getChildren() != null) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        if (field.getFldName().equals(((ExpressionTerm) item).getField())) {
                            terms.add(((ExpressionTerm) item).getValue());
                        }
                    } else if (item instanceof ExpressionOperator) {
                        getTerms((ExpressionOperator) item, field, terms);
                    }
                }
            }
        }
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
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
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
                metaListPresenter.setExpression(
                        ExpressionValidator.ALL_UNLOCKED_EXPRESSION,
                        this::refresh);
            } else {
                metaListPresenter.setExpression(
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
            metaListPresenter.setExpression(
                    MetaExpressionUtil.createFeedExpression(feedRef),
                    this::refresh);
        }
    }

    private void setPipelineCriteria(final DocRef pipelineRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            showUploadButton(false);
            metaListPresenter.setExpression(
                    MetaExpressionUtil.createPipelineExpression(pipelineRef),
                    this::refresh);
        }
    }

    private void setNullCriteria() {
        showUploadButton(false);
        metaListPresenter.setExpression(
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

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Selection<Long>> handler) {
        return metaListPresenter.addDataSelectionHandler(handler);
    }

    private void showUploadButton(final boolean visible) {
        if (streamListUpload != null) {
            streamListUpload.setVisible(visible);
        }
    }

    public boolean isSomeSelected(final AbstractMetaListPresenter streamListPresenter,
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

    private void shouldEnableDelete(final AbstractMetaListPresenter metaListPresenter,
                                    final Selection<Long> streamIdSet,
                                    final Consumer<Boolean> isEnabledConsumer) {
//        if (streamIdSet != null) {
//            GWT.log("streamIdSet (" + streamIdSet.size() + ") - " + streamIdSet);
//        }
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = getStatusSet(getCriteria().getExpression());
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

    private void shouldEnableRestore(final AbstractMetaListPresenter metaListPresenter,
                                     final Selection<Long> streamIdSet,
                                     final Consumer<Boolean> isEnabledConsumer) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = getStatusSet(getCriteria().getExpression());
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

    }
}
