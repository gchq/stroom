/*
 * Copyright 2017 Crown Copyright
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
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.dom.client.NativeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.Set;

public class MetaPresenter extends MyPresenterWidget<MetaPresenter.StreamView>
        implements HasDataSelectionHandlers<Selection<Long>>, HasDocumentRead<Object>, BeginSteppingHandler {
    public static final String DATA = "DATA";
    public static final String STREAM_RELATION_LIST = "STREAM_RELATION_LIST";
    public static final String STREAM_LIST = "STREAM_LIST";

    private final MetaListPresenter metaListPresenter;
    private final MetaRelationListPresenter metaRelationListPresenter;
    private final DataPresenter dataPresenter;
    private final Provider<DataUploadPresenter> streamUploadPresenter;
    private final Provider<ExpressionPresenter> streamListFilterPresenter;
    private final ButtonView streamListFilter;

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
                         final StreamView view,
                         final MetaListPresenter metaListPresenter,
                         final MetaRelationListPresenter metaRelationListPresenter,
                         final DataPresenter dataPresenter,
                         final Provider<ExpressionPresenter> streamListFilterPresenter,
                         final Provider<DataUploadPresenter> streamUploadPresenter,
                         final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.metaListPresenter = metaListPresenter;
        this.metaRelationListPresenter = metaRelationListPresenter;
        this.streamListFilterPresenter = streamListFilterPresenter;
        this.streamUploadPresenter = streamUploadPresenter;
        this.dataPresenter = dataPresenter;

        setInSlot(STREAM_LIST, metaListPresenter);
        setInSlot(STREAM_RELATION_LIST, metaRelationListPresenter);
        setInSlot(DATA, dataPresenter);

        dataPresenter.setBeginSteppingHandler(this);
        dataPresenter.setFormatOnLoad(true);

        // Process
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_PROCESSORS_PERMISSION)) {
            streamListProcess = metaListPresenter.add(SvgPresets.PROCESS);
            streamRelationListProcess = metaRelationListPresenter.add(SvgPresets.PROCESS);
        }

        // Delete, Undelete, DE-duplicate
        if (securityContext.hasAppPermission(PermissionNames.DELETE_DATA_PERMISSION)) {
            streamListDelete = metaListPresenter.add(SvgPresets.DELETE);
            streamListDelete.setEnabled(false);
            streamRelationListDelete = metaRelationListPresenter.add(SvgPresets.DELETE);
            streamRelationListDelete.setEnabled(false);
            streamListRestore = metaListPresenter.add(SvgPresets.UNDO);
            streamListRestore.setTitle("Restore");
            streamRelationListRestore = metaRelationListPresenter.add(SvgPresets.UNDO);
            streamRelationListRestore.setTitle("Restore");
        }

        // Download
        if (securityContext.hasAppPermission(PermissionNames.EXPORT_DATA_PERMISSION)) {
            streamListDownload = metaListPresenter.add(SvgPresets.DOWNLOAD);
            streamRelationListDownload = metaRelationListPresenter.add(SvgPresets.DOWNLOAD);
        }

        // Upload
        if (securityContext.hasAppPermission(PermissionNames.IMPORT_DATA_PERMISSION)) {
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
            presenter.read(getCriteria().getExpression(),
                    MetaFields.STREAM_STORE_DOC_REF,
                    MetaFields.getAllFields());

            final PopupUiHandlers streamFilterPUH = new DefaultPopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        final ExpressionOperator expression = presenter.write();
                        if (!expression.equals(getCriteria().getExpression())) {
                            if (hasAdvancedCriteria(expression)) {
                                ConfirmEvent.fire(MetaPresenter.this,
                                        "You are setting advanced filters!  It is recommended you constrain " +
                                                "your filter (e.g. by 'Created') to avoid an expensive query.  "
                                                + "Are you sure you want to apply this advanced filter?",
                                        confirm -> {
                                            if (confirm) {
                                                setExpression(expression);
                                                HidePopupEvent.fire(MetaPresenter.this, presenter);
                                            } else {
                                                // Don't hide
                                            }
                                        });

                            } else {
                                setExpression(expression);
                                HidePopupEvent.fire(MetaPresenter.this, presenter);
                            }

                        } else {
                            // Nothing changed!
                            HidePopupEvent.fire(MetaPresenter.this, presenter);
                        }

                    } else {
                        HidePopupEvent.fire(MetaPresenter.this, presenter);
                    }
                }

                private void setExpression(final ExpressionOperator expression) {
                    // Copy new filter settings back.
                    getCriteria().setExpression(expression);
                    // Reset the page offset.
                    getCriteria().obtainPageRequest().setOffset(0L);

                    // Init the buttons
                    setStreamListSelectableEnabled(metaListPresenter.getSelection());

                    // Clear the current selection and get a new list of streams.
                    metaListPresenter.getSelectionModel().clear();
                    metaListPresenter.refresh();
                }
            };

            final PopupSize popupSize = new PopupSize(
                    800, 600, 400, 400, true);
            ShowPopupEvent.fire(
                    MetaPresenter.this,
                    presenter,
                    PopupType.OK_CANCEL_DIALOG,
                    popupSize,
                    "Filter Streams",
                    streamFilterPUH);
        }));

        // Some button's may not exist due to permissions
        if (streamListUpload != null) {
            registerHandler(streamListUpload.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    streamUploadPresenter.get().show(MetaPresenter.this, feedRef);
                }
            }));
        }
        if (streamListDownload != null) {
            registerHandler(streamListDownload.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaListPresenter.download();
                }
            }));
        }
        if (streamRelationListDownload != null) {
            registerHandler(streamRelationListDownload.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaRelationListPresenter.download();
                }
            }));
        }
        // Delete
        if (streamListDelete != null) {
            registerHandler(streamListDelete.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaListPresenter.delete();
                }
            }));
        }
        if (streamRelationListDelete != null) {
            registerHandler(streamRelationListDelete.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaRelationListPresenter.delete();
                }
            }));
        }
        // Restore
        if (streamListRestore != null) {
            registerHandler(streamListRestore.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaListPresenter.restore();
                }
            }));
        }
        if (streamRelationListRestore != null) {
            registerHandler(streamRelationListRestore.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaRelationListPresenter.restore();
                }
            }));
        }
        // Process
        if (streamListProcess != null) {
            registerHandler(streamListProcess.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaListPresenter.process();
                }
            }));
        }
        if (streamRelationListProcess != null) {
            registerHandler(streamRelationListProcess.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    metaRelationListPresenter.process();
                }
            }));
        }
    }

    public boolean hasAdvancedCriteria(final ExpressionOperator expression) {
        final Status status = getSingleStatus(expression);

        if (!Status.UNLOCKED.equals(status)) {
            return true;
        }

        final Set<String> statusPeriod = getTerms(expression, MetaFields.STATUS_TIME);
        return statusPeriod.size() > 0;
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

    private static Set<String> getTerms(final ExpressionOperator expression, final AbstractField field) {
        final Set<String> terms = new HashSet<>();
        if (expression != null) {
            getTerms(expression, field, terms);
        }
        return terms;
    }

    private static void getTerms(final ExpressionOperator expressionOperator,
                                 final AbstractField field,
                                 final Set<String> terms) {
        if (expressionOperator.enabled()) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        if (field.getName().equals(((ExpressionTerm) item).getField())) {
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
        // Get a new list of streams.
        metaListPresenter.refresh();
        metaRelationListPresenter.refresh();
    }

    public FindMetaCriteria getCriteria() {
        return metaListPresenter.getCriteria();
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        if (entity instanceof FeedDoc) {
            setFeedCriteria(docRef);
        } else if (entity instanceof PipelineDoc) {
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
            showStreamListButtons(true);
            showStreamRelationListButtons(true);

            metaListPresenter.setExpression(MetaExpressionUtil.createFolderExpression(folder));

            refresh();
        }
    }

    private void setFeedCriteria(final DocRef feedRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            this.feedRef = feedRef;
            showStreamListButtons(true);
            showStreamRelationListButtons(true);

            metaListPresenter.setExpression(MetaExpressionUtil.createFeedExpression(feedRef.getName()));

            refresh();
        }
    }

    private void setPipelineCriteria(final DocRef pipelineRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            showStreamListButtons(false);
            showStreamRelationListButtons(false);

            metaListPresenter.setExpression(MetaExpressionUtil.createPipelineExpression(pipelineRef));

            refresh();
        }
    }

    private void setNullCriteria() {
        showStreamListButtons(false);
        showStreamRelationListButtons(false);

        metaListPresenter.setExpression(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED));

        refresh();
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

    private void showStreamListButtons(final boolean visible) {
        if (streamListUpload != null) {
            streamListUpload.setVisible(visible);
        }
    }

    private void showStreamRelationListButtons(final boolean visible) {
    }

    public boolean isSomeSelected(final AbstractMetaListPresenter streamListPresenter,
                                  final Selection<Long> selectedIdSet) {
        if (streamListPresenter.getResultPage() == null || streamListPresenter.getResultPage().size() == 0) {
            return false;
        }
        return selectedIdSet != null && !selectedIdSet.isMatchNothing();
    }

    public void setStreamListSelectableEnabled(final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);

        if (streamListDownload != null) {
            streamListDownload.setEnabled(someSelected);
        }
        if (streamListDelete != null) {
            final boolean enabled = shouldEnableDelete(metaListPresenter, streamIdSet);
            streamListDelete.setEnabled(enabled);
        }
        if (streamListProcess != null) {
            streamListProcess.setEnabled(someSelected);
        }
        if (streamListRestore != null) {
            final boolean enabled = shouldEnableRestore(metaListPresenter, streamIdSet);
            streamListRestore.setEnabled(enabled);
        }
    }

    private void setStreamRelationListSelectableEnabled(final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaRelationListPresenter, streamIdSet);

        if (streamRelationListDownload != null) {
            streamRelationListDownload.setEnabled(someSelected);
        }
        if (streamRelationListDelete != null) {
            final boolean enabled = shouldEnableDelete(metaRelationListPresenter, streamIdSet);
            streamRelationListDelete.setEnabled(enabled);
        }
        if (streamRelationListRestore != null) {
            final boolean enabled = shouldEnableRestore(metaRelationListPresenter, streamIdSet);
            streamRelationListRestore.setEnabled(enabled);
        }
        if (streamRelationListProcess != null) {
            streamRelationListProcess.setEnabled(someSelected);
        }
    }

    private boolean shouldEnableDelete(final AbstractMetaListPresenter metaListPresenter,
                                       final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = getStatusSet(getCriteria().getExpression());
            final boolean allowDelete = statusSet.size() == 0 ||
                    statusSet.contains(Status.LOCKED) ||
                    statusSet.contains(Status.UNLOCKED);

            if (allowDelete) {
                if (streamIdSet != null) {
                    if (streamIdSet.isMatchAll()) {
                        return true;
                    } else {
                        for (final Long id : streamIdSet.getSet()) {
                            final Meta meta = getMeta(metaListPresenter, id);
                            if (meta != null && !Status.DELETED.equals(meta.getStatus())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldEnableRestore(final AbstractMetaListPresenter metaListPresenter,
                                        final Selection<Long> streamIdSet) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);
        if (someSelected) {
            final Set<Status> statusSet = getStatusSet(getCriteria().getExpression());
            final boolean allowRestore = statusSet.size() == 0 ||
                    statusSet.contains(Status.DELETED);

            if (allowRestore) {
                if (streamIdSet != null) {
                    if (streamIdSet.isMatchAll()) {
                        return true;
                    } else {
                        for (final Long id : streamIdSet.getSet()) {
                            final Meta meta = getMeta(metaListPresenter, id);
                            if (meta != null && Status.DELETED.equals(meta.getStatus())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void beginStepping(final long streamId, final String childStreamType) {
        // Try and get a pipeline id to use as a starting point for
        // stepping.
        DocRef pipelineRef = null;

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
            if (childMeta.getParentMetaId() != null && childMeta.getParentMetaId().equals(streamId)) {
                childStreamId = childMeta.getId();
            }
        }

        BeginPipelineSteppingEvent.fire(
                this,
                streamId,
                childStreamId,
                childStreamType,
                new StepLocation(streamId, 1, 0),
                pipelineRef);
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        dataPresenter.setClassificationUiHandlers(classificationUiHandlers);
    }

    public interface StreamView extends View {
    }
}
