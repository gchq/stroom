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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.LocationManager;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.shared.DataResource;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;

import stroom.entity.client.presenter.HasDocumentRead;
import stroom.explorer.shared.SharedDocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.IdSet;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetaPresenter extends MyPresenterWidget<MetaPresenter.StreamView>
        implements HasDataSelectionHandlers<IdSet>, HasDocumentRead<Object>, BeginSteppingHandler {
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    public static final String DATA = "DATA";
    public static final String STREAM_RELATION_LIST = "STREAM_RELATION_LIST";
    public static final String STREAM_LIST = "STREAM_LIST";

    private final LocationManager locationManager;
    private final MetaListPresenter metaListPresenter;
    private final MetaRelationListPresenter metaRelationListPresenter;
    private final DataPresenter dataPresenter;
    private final Provider<DataUploadPresenter> streamUploadPresenter;
    private final Provider<ExpressionPresenter> streamListFilterPresenter;
    private final RestFactory restFactory;
    private final ButtonView streamListFilter;

    private FindMetaCriteria findMetaCriteria;
    private DocRef feedRef;
    private ButtonView streamListUpload;
    private ButtonView streamListDownload;
    private ButtonView streamListDelete;
    private ButtonView streamListProcess;
    private ButtonView streamListUndelete;
    private ButtonView streamRelationListDownload;
    private ButtonView streamRelationListDelete;
    private ButtonView streamRelationListUndelete;
    private ButtonView streamRelationListProcess;

    private boolean hasSetCriteria;

    @Inject
    public MetaPresenter(final EventBus eventBus,
                         final StreamView view,
                         final LocationManager locationManager,
                         final MetaListPresenter metaListPresenter,
                         final MetaRelationListPresenter metaRelationListPresenter,
                         final DataPresenter dataPresenter,
                         final Provider<ExpressionPresenter> streamListFilterPresenter,
                         final Provider<DataUploadPresenter> streamUploadPresenter,
                         final RestFactory restFactory,
                         final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.locationManager = locationManager;
        this.metaListPresenter = metaListPresenter;
        this.metaRelationListPresenter = metaRelationListPresenter;
        this.streamListFilterPresenter = streamListFilterPresenter;
        this.streamUploadPresenter = streamUploadPresenter;
        this.dataPresenter = dataPresenter;
        this.restFactory = restFactory;

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
            streamListUndelete = metaListPresenter.add(SvgPresets.UNDO);
            streamListUndelete.setTitle("Un-Delete");
            streamRelationListUndelete = metaRelationListPresenter.add(SvgPresets.UNDO);
            streamRelationListUndelete.setTitle("un-Delete");
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
        setStreamListSelectableEnabled(null, Status.UNLOCKED);
        setStreamRelationListSelectableEnabled(null, Status.UNLOCKED);
    }

//    public static FindMetaCriteria createFindMetaCriteria() {
//        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
//        findMetaCriteria.obtainExpression();
//        return findMetaCriteria;
//    }

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

    public static boolean isSelectedAllOfStatus(final Status filterStatus,
                                                final AbstractMetaListPresenter streamListPresenter, final IdSet selectedIdSet,
                                                final Status... statusArray) {
        final List<Status> statusList = Arrays.asList(statusArray);
        // Nothing Selected
        if (selectedIdSet == null || selectedIdSet.isMatchNothing()) {
            return false;
        }
        // Match All must be a status that we expect
        if (Boolean.TRUE.equals(selectedIdSet.getMatchAll())) {
            if (filterStatus == null) {
                return false;
            }
            return statusList.contains(filterStatus);
        }

        for (final Long id : selectedIdSet.getSet()) {
            final Meta meta = getMeta(streamListPresenter, id);
            if (meta == null || !statusList.contains(meta.getStatus())) {
                return false;
            }
        }

        return true;

    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(metaListPresenter.getSelectionModel().addSelectionHandler(event -> {
            metaRelationListPresenter.setSelectedStream(metaListPresenter.getSelected(), true,
                    !Status.UNLOCKED.equals(getSingleStatus(getCriteria())));
            showData();
        }));
        registerHandler(metaListPresenter.addDataSelectionHandler(event -> setStreamListSelectableEnabled(event.getSelectedItem(),
                getSingleStatus(findMetaCriteria))));
        registerHandler(metaRelationListPresenter.getSelectionModel().addSelectionHandler(event -> showData()));
        registerHandler(metaRelationListPresenter.addDataSelectionHandler(event -> setStreamRelationListSelectableEnabled(event.getSelectedItem(), getSingleStatus(findMetaCriteria))));

        registerHandler(streamListFilter.addClickHandler(event -> {
            final ExpressionPresenter presenter = streamListFilterPresenter.get();
            presenter.read(findMetaCriteria.getExpression(),
                    MetaFields.STREAM_STORE_DOC_REF,
                    MetaFields.getAllFields());

            final PopupUiHandlers streamFilterPUH = new DefaultPopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        final ExpressionOperator expression = presenter.write();

                        if (!expression.equals(findMetaCriteria.getExpression())) {
                            if (hasAdvancedCriteria(expression)) {
                                ConfirmEvent.fire(MetaPresenter.this,
                                        "You are setting advanced filters!  It is recommendend you constrain your filter (e.g. by 'Created') to avoid an expensive query.  "
                                                + "Are you sure you want to apply this advanced filter?",
                                        confirm -> {
                                            if (confirm) {
                                                applyCriteriaAndShow(presenter);
                                                HidePopupEvent.fire(MetaPresenter.this, presenter);
                                            } else {
                                                // Don't hide
                                            }
                                        });

                            } else {
                                applyCriteriaAndShow(presenter);
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

                private void applyCriteriaAndShow(final ExpressionPresenter presenter) {
                    // Copy new filter settings back.
                    findMetaCriteria.setExpression(presenter.write());
                    // Reset the page offset.
                    findMetaCriteria.obtainPageRequest().setOffset(0L);

                    // Init the buttons
                    final Status status = getSingleStatus(findMetaCriteria);
                    setStreamListSelectableEnabled(metaListPresenter.getSelectedEntityIdSet(), status);

                    // Clear the current selection and get a new list of streams.
                    metaListPresenter.getSelectionModel().clear();
                    metaListPresenter.refresh();
                }
            };

            final PopupSize popupSize = new PopupSize(800, 600, 400, 400, true);
            ShowPopupEvent.fire(MetaPresenter.this, presenter, PopupType.OK_CANCEL_DIALOG, popupSize,
                    "Filter Streams", streamFilterPUH);
        }));

        // Some button's may not exist due to permissions
        if (streamListUpload != null) {
            registerHandler(streamListUpload.addClickHandler(event -> streamUploadPresenter.get().show(MetaPresenter.this, feedRef)));
        }
        if (streamListDownload != null) {
            registerHandler(streamListDownload
                    .addClickHandler(new DownloadStreamClickHandler(this, metaListPresenter, true, restFactory, locationManager)));
        }
        if (streamRelationListDownload != null) {
            registerHandler(streamRelationListDownload.addClickHandler(
                    new DownloadStreamClickHandler(this, metaRelationListPresenter, false, restFactory, locationManager)));
        }
        // Delete
        if (streamListDelete != null) {
            registerHandler(streamListDelete
                    .addClickHandler(new UpdateStatusClickHandler(this, metaListPresenter, true, restFactory, Status.DELETED)));
        }
        if (streamRelationListDelete != null) {
            registerHandler(streamRelationListDelete.addClickHandler(
                    new UpdateStatusClickHandler(this, metaRelationListPresenter, false, restFactory, Status.DELETED)));
        }
        // UN-Delete
        if (streamListUndelete != null) {
            registerHandler(streamListUndelete
                    .addClickHandler(new UpdateStatusClickHandler(this, metaListPresenter, true, restFactory, Status.UNLOCKED)));
        }
        if (streamRelationListUndelete != null) {
            registerHandler(streamRelationListUndelete.addClickHandler(
                    new UpdateStatusClickHandler(this, metaRelationListPresenter, false, restFactory, Status.UNLOCKED)));
        }
        // Process
        if (streamListProcess != null) {
            registerHandler(streamListProcess
                    .addClickHandler(new ProcessStreamClickHandler(this, metaListPresenter, true, restFactory)));
        }
        if (streamRelationListProcess != null) {
            registerHandler(streamRelationListProcess.addClickHandler(
                    new ProcessStreamClickHandler(this, metaRelationListPresenter, false, restFactory)));
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

    private static void getTerms(final ExpressionOperator expressionOperator, final AbstractField field, final Set<String> terms) {
        if (expressionOperator.isEnabled()) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.isEnabled()) {
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
    }

    public FindMetaCriteria getCriteria() {
        return findMetaCriteria;
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

            findMetaCriteria = new FindMetaCriteria(MetaExpressionUtil.createFolderExpression(folder));

            initCriteria();
        }
    }

    private void setFeedCriteria(final DocRef feedRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            this.feedRef = feedRef;
            showStreamListButtons(true);
            showStreamRelationListButtons(true);

            findMetaCriteria = new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(feedRef.getName()));

            initCriteria();
        }
    }

    private void setPipelineCriteria(final DocRef pipelineRef) {
        // Only set this criteria once.
        if (!hasSetCriteria) {
            hasSetCriteria = true;
            showStreamListButtons(false);
            showStreamRelationListButtons(false);

            findMetaCriteria = new FindMetaCriteria(MetaExpressionUtil.createPipelineExpression(pipelineRef));

            initCriteria();
        }
    }

    private void setNullCriteria() {
        showStreamListButtons(false);
        showStreamRelationListButtons(false);
        findMetaCriteria = new FindMetaCriteria();

        initCriteria();
    }

    private void initCriteria() {
        metaListPresenter.setCriteria(findMetaCriteria);
        metaRelationListPresenter.setCriteria(null);
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

//    public IdSet getSelectedEntityIdSet() {
//        return metaListPresenter.getSelectedEntityIdSet();
//    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<IdSet> handler) {
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
                                  final IdSet selectedIdSet) {
        if (streamListPresenter.getResultPage() == null || streamListPresenter.getResultPage().size() == 0) {
            return false;
        }
        return selectedIdSet != null && (Boolean.TRUE.equals(selectedIdSet.getMatchAll()) || selectedIdSet.size() > 0);
    }

    public void setStreamListSelectableEnabled(final IdSet streamIdSet, final Status streamStatus) {
        final boolean someSelected = isSomeSelected(metaListPresenter, streamIdSet);

        if (streamListDownload != null) {
            streamListDownload.setEnabled(someSelected);
        }
        if (streamListDelete != null) {
            streamListDelete
                    .setEnabled(someSelected);
            // && isSelectedAllOfStatus(getCriteria().obtainStatusSet().getSingleItem(),
//                            metaListPresenter, streamIdSet, StreamStatus.LOCKED, StreamStatus.UNLOCKED));
        }
        if (streamListProcess != null) {
            streamListProcess.setEnabled(someSelected);
        }
        if (streamListUndelete != null) {
            // Hide if we are normal view (Unlocked streams)
            streamListUndelete.setVisible(!Status.UNLOCKED.equals(streamStatus));
            streamListUndelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getSingleStatus(getCriteria()),
                            metaListPresenter, streamIdSet, Status.DELETED));
        }

    }

    private void setStreamRelationListSelectableEnabled(final IdSet streamIdSet,
                                                        final Status streamStatus) {
        final boolean someSelected = isSomeSelected(metaRelationListPresenter, streamIdSet);

        if (streamRelationListDownload != null) {
            streamRelationListDownload.setEnabled(someSelected);
        }
        if (streamRelationListDelete != null) {
            streamRelationListDelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getSingleStatus(getCriteria()),
                            metaRelationListPresenter, streamIdSet, Status.LOCKED, Status.UNLOCKED));
        }
        if (streamRelationListUndelete != null) {
            // Hide if we are normal view (Unlocked streams)
            streamRelationListUndelete.setVisible(!Status.UNLOCKED.equals(streamStatus));
            streamRelationListUndelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getSingleStatus(getCriteria()),
                            metaRelationListPresenter, streamIdSet, Status.DELETED));
        }
        if (streamRelationListProcess != null) {
            streamRelationListProcess.setEnabled(someSelected);
        }
    }

    @Override
    public void beginStepping(final long streamId, final String childStreamType) {
        // Try and get a pipeline id to use as a starting point for
        // stepping.
        SharedDocRef pipelineRef = null;

        // TODO : Fix by making entity id sets docref sets.
//            final EntityIdSet<PipelineEntity> entityIdSet = findMetaCriteria
//                    .getPipelineUuidCriteria();
//            if (entityIdSet != null) {
//                if (entityIdSet.getSet().size() > 0) {
//                    pipelineRef = DocRef.create(entityIdSet.getSet().iterator().next());
//                }
//            }

        // We will assume that the stream list has a child stream selected.
        // This would be the case where a user chooses an event with errors
        // in the top screen and then chooses the raw stream in the middle
        // pane to step through.
        Long childStreamId = null;
        final MetaRow map = metaListPresenter.getSelected();
        if (map != null && map.getMeta() != null) {
            final Meta childMeta = map.getMeta();
            // If the top list has a raw stream selected or isn't a child of
            // the selected stream then this is't the child stream we are
            // looking for.
            if (childMeta.getParentMetaId() != null && childMeta.getParentMetaId().equals(streamId)) {
                childStreamId = childMeta.getId();
            }
        }

        BeginPipelineSteppingEvent.fire(this, streamId, childStreamId, childStreamType, new StepLocation(streamId, 1, 0), pipelineRef);
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        dataPresenter.setClassificationUiHandlers(classificationUiHandlers);
    }

    public interface StreamView extends View {
    }

    private static abstract class AbstractStreamClickHandler implements ClickHandler, HasHandlers {
        private final MetaPresenter metaPresenter;
        private final AbstractMetaListPresenter streamListPresenter;
        private final boolean useCriteria;
        private final RestFactory restFactory;

        AbstractStreamClickHandler(final MetaPresenter metaPresenter,
                                   final AbstractMetaListPresenter streamListPresenter, final boolean useCriteria,
                                   final RestFactory restFactory) {
            this.metaPresenter = metaPresenter;
            this.streamListPresenter = streamListPresenter;
            this.useCriteria = useCriteria;
            this.restFactory = restFactory;
        }

        AbstractMetaListPresenter getStreamListPresenter() {
            return streamListPresenter;
        }

        FindMetaCriteria createCriteria() {
            final IdSet idSet = streamListPresenter.getSelectedEntityIdSet();
            // First make sure there is some sort of selection, either
            // individual streams have been selected or all streams have been
            // selected.
            if (Boolean.TRUE.equals(idSet.getMatchAll()) || idSet.size() > 0) {
                // Only use match all if we are allowed to use criteria.
                if (useCriteria && Boolean.TRUE.equals(idSet.getMatchAll())) {
                    final FindMetaCriteria criteria = new FindMetaCriteria();
                    criteria.copyFrom(metaPresenter.getCriteria());
                    // Paging is NA
                    criteria.obtainPageRequest().setLength(null);
                    criteria.obtainPageRequest().setOffset(null);
                    return criteria;

                } else if (idSet.size() > 0) {
                    // If we aren't matching all then create a criteria that
                    // only includes the selected streams.
                    final FindMetaCriteria criteria = new FindMetaCriteria();
                    // Copy the current filter status
                    final Status status = getSingleStatus(metaPresenter.getCriteria());
                    if (status != null) {
                        criteria.setExpression(MetaExpressionUtil.createStatusExpression(status));
                    }
                    criteria.obtainSelectedIdSet().addAll(idSet.getSet());
                    // Paging is NA
                    criteria.obtainPageRequest().setLength(null);
                    criteria.obtainPageRequest().setOffset(null);
                    return criteria;
                }
            }

            return null;
        }

        @Override
        public void onClick(final ClickEvent event) {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final FindMetaCriteria criteria = createCriteria();
                if (criteria != null) {
                    performAction(criteria, restFactory);
                }
            }
        }

        protected abstract void performAction(FindMetaCriteria criteria, RestFactory restFactory);

        @Override
        public void fireEvent(final GwtEvent<?> event) {
            metaPresenter.fireEvent(event);
        }

        protected void refreshList() {
            streamListPresenter.refresh();
        }
    }

    private static class DownloadStreamClickHandler extends AbstractStreamClickHandler {
        private final LocationManager locationManager;

        public DownloadStreamClickHandler(final MetaPresenter streamPresenter,
                                          final AbstractMetaListPresenter streamListPresenter, final boolean useCriteria,
                                          final RestFactory restFactory, final LocationManager locationManager) {
            super(streamPresenter, streamListPresenter, useCriteria, restFactory);
            this.locationManager = locationManager;
        }

        @Override
        protected void performAction(final FindMetaCriteria criteria, final RestFactory restFactory) {
            final Rest<ResourceGeneration> rest = restFactory.create();
            rest
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, null, result))
                    .call(DATA_RESOURCE)
                    .download(criteria);
        }
    }

    private static class UpdateStatusClickHandler extends AbstractStreamClickHandler {
        private final Status newStatus;

        public UpdateStatusClickHandler(final MetaPresenter streamPresenter,
                                        final AbstractMetaListPresenter streamListPresenter,
                                        final boolean useCriteria,
                                        final RestFactory restFactory,
                                        final Status newStatus) {
            super(streamPresenter, streamListPresenter, useCriteria, restFactory);
            this.newStatus = newStatus;
        }

        protected String getText(final boolean pastTense) {
            if (Status.DELETED.equals(newStatus)) {
                return "Delete" + (pastTense ? "d" : "");
            } else {
                return "Restore" + (pastTense ? "d" : "");
            }
        }

        @Override
        protected void performAction(final FindMetaCriteria initialCriteria, final RestFactory restFactory) {
            final FindMetaCriteria deleteCriteria = new FindMetaCriteria();
            deleteCriteria.copyFrom(initialCriteria);

//            if (getSingleStatus(deleteCriteria) == null) {
//                for (final StreamStatus streamStatusToCheck : StreamStatus.values()) {
//                    if (isSelectedAllOfStatus(null, getStreamListPresenter(), deleteCriteria.getSelectedIdSet(),
//                            streamStatusToCheck)) {
//                        deleteCriteria.obtainStatusSet().setSingleItem(streamStatusToCheck);
//                    }
//                }
//            }
            if (getSingleStatus(deleteCriteria) == null) {
                AlertEvent.fireError(this, "Unable to action command on mixed status", null);
            } else {
                ConfirmEvent.fire(this,
                        "Are you sure you want to " + getText(false).toLowerCase() + " the selected items?",
                        confirm -> {
                            if (confirm) {
                                if (!deleteCriteria.getSelectedIdSet().isConstrained()) {
                                    ConfirmEvent.fireWarn(UpdateStatusClickHandler.this,
                                            "You have selected all items.  Are you sure you want to "
                                                    + getText(false).toLowerCase() + " all the selected items?",
                                            confirm1 -> {
                                                if (confirm1) {
                                                    doUpdate(deleteCriteria, restFactory);
                                                }
                                            });

                                } else {
                                    doUpdate(deleteCriteria, restFactory);
                                }
                            }
                        });
            }
        }

        void doUpdate(final FindMetaCriteria criteria, final RestFactory restFactory) {
            final Rest<Integer> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        getStreamListPresenter().getSelectedEntityIdSet().clear();
                        getStreamListPresenter().getSelectedEntityIdSet().setMatchAll(false);

                        AlertEvent.fireInfo(UpdateStatusClickHandler.this,
                                getText(true) + " " + result + " record" + ((result.longValue() > 1) ? "s" : ""), this::refreshList);
                    })
                    .call(META_RESOURCE)
                    .updateStatus(new UpdateStatusRequest(criteria, newStatus));
        }
    }

    private static class ProcessStreamClickHandler extends AbstractStreamClickHandler {
        ProcessStreamClickHandler(final MetaPresenter streamPresenter,
                                  final AbstractMetaListPresenter streamListPresenter, final boolean useCriteria,
                                  final RestFactory restFactory) {
            super(streamPresenter, streamListPresenter, useCriteria, restFactory);
        }

        @Override
        protected void performAction(final FindMetaCriteria criteria, final RestFactory restFactory) {
            if (criteria != null) {
                ConfirmEvent.fire(this, "Are you sure you want to reprocess the selected items", confirm -> {
                    if (confirm) {
                        final Rest<List<ReprocessDataInfo>> rest = restFactory.create();
                        rest
                                .onSuccess(result -> {
                                    if (result != null && result.size() > 0) {
                                        for (final ReprocessDataInfo info : result) {
                                            switch (info.getSeverity()) {
                                                case INFO:
                                                    AlertEvent.fireInfo(ProcessStreamClickHandler.this, info.getMessage(),
                                                            info.getDetails(), null);
                                                    break;
                                                case WARNING:
                                                    AlertEvent.fireWarn(ProcessStreamClickHandler.this, info.getMessage(),
                                                            info.getDetails(), null);
                                                    break;
                                                case ERROR:
                                                    AlertEvent.fireError(ProcessStreamClickHandler.this, info.getMessage(),
                                                            info.getDetails(), null);
                                                    break;
                                                case FATAL_ERROR:
                                                    AlertEvent.fireError(ProcessStreamClickHandler.this, info.getMessage(),
                                                            info.getDetails(), null);
                                                    break;
                                            }
                                        }
                                    }
                                })
                                .call(PROCESSOR_FILTER_RESOURCE)
                                .reprocess(criteria);
                    }
                });
            }
        }
    }
}
