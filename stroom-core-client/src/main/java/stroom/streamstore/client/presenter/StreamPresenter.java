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

package stroom.streamstore.client.presenter;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.AlertCallback;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteHandler;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceFindDeleteAction;
import stroom.entity.shared.Folder;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.ResultList;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.DownloadDataAction;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.ReprocessDataAction;
import stroom.streamstore.shared.ReprocessDataInfo;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedLong;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Arrays;
import java.util.List;

public class StreamPresenter extends MyPresenterWidget<StreamPresenter.StreamView>
        implements HasDataSelectionHandlers<EntityIdSet<Stream>>, HasRead<BaseEntity>, BeginSteppingHandler {
    public static final String DATA = "DATA";
    public static final String STREAM_RELATION_LIST = "STREAM_RELATION_LIST";
    public static final String STREAM_LIST = "STREAM_LIST";

    private final StreamListPresenter streamListPresenter;
    private final StreamRelationListPresenter streamRelationListPresenter;
    private final DataPresenter dataPresenter;
    private final Provider<StreamUploadPresenter> streamUploadPresenter;
    private final Provider<StreamFilterPresenter> streamListFilterPresenter;
    private final ClientDispatchAsync dispatcher;
    private final GlyphButtonView streamListFilter;

    private boolean folderVisible;
    private boolean feedVisible;
    private boolean pipelineVisible;
    private FindStreamAttributeMapCriteria findStreamAttributeMapCriteria;
    private Feed feedCriteria;
    private GlyphButtonView streamListUpload;
    private GlyphButtonView streamListDownload;
    private GlyphButtonView streamListDelete;
    private ImageButtonView streamListProcess;
    private GlyphButtonView streamListUndelete;
    private GlyphButtonView streamRelationListDownload;
    private GlyphButtonView streamRelationListDelete;
    private GlyphButtonView streamRelationListUndelete;
    private ImageButtonView streamRelationListProcess;

    @Inject
    public StreamPresenter(final EventBus eventBus, final StreamView view,
                           final StreamListPresenter streamListPresenter,
                           final StreamRelationListPresenter streamRelationListPresenter, final DataPresenter dataPresenter,
                           final Provider<StreamFilterPresenter> streamListFilterPresenter,
                           final Provider<StreamUploadPresenter> streamUploadPresenter, final Resources resources,
                           final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.streamListPresenter = streamListPresenter;
        this.streamRelationListPresenter = streamRelationListPresenter;
        this.streamListFilterPresenter = streamListFilterPresenter;
        this.streamUploadPresenter = streamUploadPresenter;
        this.dataPresenter = dataPresenter;
        this.dispatcher = dispatcher;

        setInSlot(STREAM_LIST, streamListPresenter);
        setInSlot(STREAM_RELATION_LIST, streamRelationListPresenter);
        setInSlot(DATA, dataPresenter);

        dataPresenter.setBeginSteppingHandler(this);

        // Process
        if (securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            streamListProcess = streamListPresenter.add("Process", resources.pipeline(), resources.pipelineDisabled(),
                    false);
            streamRelationListProcess = streamRelationListPresenter.add("Process", resources.pipeline(),
                    resources.pipelineDisabled(), false);
        }

        // Delete, Undelete, DE-duplicate
        if (securityContext.hasAppPermission(Stream.DELETE_DATA_PERMISSION)) {
            streamListDelete = streamListPresenter.add(GlyphIcons.DELETE);
            streamListDelete.setEnabled(false);
            streamRelationListDelete = streamRelationListPresenter.add(GlyphIcons.DELETE);
            streamRelationListDelete.setEnabled(false);
            streamListUndelete = streamListPresenter.add(GlyphIcons.UNDO);
            streamListUndelete.setTitle("Un-Delete");
            streamRelationListUndelete = streamRelationListPresenter.add(GlyphIcons.UNDO);
            streamRelationListUndelete.setTitle("un-Delete");
        }

        // Download
        if (securityContext.hasAppPermission(Stream.EXPORT_DATA_PERMISSION)) {
            streamListDownload = streamListPresenter.add(GlyphIcons.DOWNLOAD);
            streamRelationListDownload = streamRelationListPresenter.add(GlyphIcons.DOWNLOAD);
        }

        // Upload
        if (securityContext.hasAppPermission(Stream.IMPORT_DATA_PERMISSION)) {
            streamListUpload = streamListPresenter.add(GlyphIcons.UPLOAD);
        }

        // Filter
        streamListFilter = streamListPresenter.add(GlyphIcons.FILTER);

        // Init the buttons
        setStreamListSelectableEnabled(null, StreamStatus.UNLOCKED);
        setStreamRelationListSelectableEnabled(null, StreamStatus.UNLOCKED);
    }

    public static FindStreamCriteria createFindStreamCriteria() {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);
        return findStreamCriteria;
    }

    private static Stream getStream(final AbstractStreamListPresenter streamListPresenter, final long id) {
        final ResultList<StreamAttributeMap> list = streamListPresenter.getResultList();
        if (list != null) {
            if (list.getValues() != null) {
                for (final StreamAttributeMap streamAttributeMap : list.getValues()) {
                    if (streamAttributeMap.getStream().getId() == id) {
                        return streamAttributeMap.getStream();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isSelectedAllOfStatus(final StreamStatus filterStatus,
                                                final AbstractStreamListPresenter streamListPresenter, final EntityIdSet<Stream> selectedIdSet,
                                                final StreamStatus... statusArray) {
        final List<StreamStatus> statusList = Arrays.asList(statusArray);
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
            final Stream stream = getStream(streamListPresenter, id);
            if (stream == null || !statusList.contains(stream.getStatus())) {
                return false;
            }
        }

        return true;

    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(streamListPresenter.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                streamRelationListPresenter.setSelectedStream(streamListPresenter.getSelectedStream(), true,
                        !StreamStatus.UNLOCKED.equals(getCriteria().obtainStatusSet().getSingleItem()));
                showData();
            }
        }));
        registerHandler(streamListPresenter.addDataSelectionHandler(new DataSelectionHandler<EntityIdSet<Stream>>() {
            @Override
            public void onSelection(final DataSelectionEvent<EntityIdSet<Stream>> event) {
                setStreamListSelectableEnabled(event.getSelectedItem(),
                        findStreamAttributeMapCriteria.getFindStreamCriteria().obtainStatusSet().getSingleItem());
            }
        }));
        registerHandler(streamRelationListPresenter.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                showData();
            }
        }));
        registerHandler(
                streamRelationListPresenter.addDataSelectionHandler(new DataSelectionHandler<EntityIdSet<Stream>>() {
                    @Override
                    public void onSelection(final DataSelectionEvent<EntityIdSet<Stream>> event) {
                        setStreamRelationListSelectableEnabled(event.getSelectedItem(), findStreamAttributeMapCriteria
                                .getFindStreamCriteria().obtainStatusSet().getSingleItem());
                    }
                }));

        registerHandler(streamListFilter.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                final StreamFilterPresenter presenter = streamListFilterPresenter.get();
                presenter.setCriteria(findStreamAttributeMapCriteria, folderVisible, feedVisible, pipelineVisible, true,
                        true);

                final PopupUiHandlers streamFilterPUH = new DefaultPopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            presenter.write();

                            if (!presenter.getCriteria().equals(findStreamAttributeMapCriteria)) {
                                if (hasAdvancedCriteria(presenter.getCriteria())) {
                                    ConfirmEvent.fire(StreamPresenter.this,
                                            "You are setting advanced filters!  It is recommendend you constrain your filter (e.g. by 'Created') to avoid an expensive query.  "
                                                    + "Are you sure you want to apply this advanced filter?",
                                            new ConfirmCallback() {
                                                @Override
                                                public void onResult(final boolean confirm) {
                                                    if (confirm) {
                                                        applyCriteriaAndShow(presenter);
                                                        HidePopupEvent.fire(StreamPresenter.this, presenter);
                                                    } else {
                                                        // Don't hide
                                                    }
                                                }

                                            });

                                } else {
                                    applyCriteriaAndShow(presenter);
                                    HidePopupEvent.fire(StreamPresenter.this, presenter);
                                }

                            } else {
                                // Nothing changed!
                                HidePopupEvent.fire(StreamPresenter.this, presenter);
                            }

                        } else {
                            HidePopupEvent.fire(StreamPresenter.this, presenter);
                        }
                    }

                    private void applyCriteriaAndShow(final StreamFilterPresenter presenter) {
                        // Copy new filter settings back.
                        findStreamAttributeMapCriteria.copyFrom(presenter.getCriteria());
                        // Reset the page offset.
                        findStreamAttributeMapCriteria.obtainPageRequest().setOffset(0L);

                        // Init the buttons
                        setStreamListSelectableEnabled(streamListPresenter.getSelectedEntityIdSet(),
                                findStreamAttributeMapCriteria.getFindStreamCriteria().obtainStatusSet()
                                        .getSingleItem());

                        // Get a new list of streams.
                        streamListPresenter.refresh();

                        // If something is selected refresh the
                        // relations
                        if (streamListPresenter.getSelectedStream() != null) {
                            streamRelationListPresenter.setSelectedStream(streamListPresenter.getSelectedStream(), true,
                                    !StreamStatus.UNLOCKED.equals(getCriteria().obtainStatusSet().getSingleItem()));
                        }

                        showData();
                    }
                };

                final PopupSize popupSize = new PopupSize(412, 600, 412, 600, true);
                ShowPopupEvent.fire(StreamPresenter.this, presenter, PopupType.OK_CANCEL_DIALOG, popupSize,
                        "Filter Streams", streamFilterPUH);
            }
        }));

        // Some button's may not exist due to permissions
        if (streamListUpload != null) {
            registerHandler(streamListUpload.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    streamUploadPresenter.get().show(StreamPresenter.this, DocRefUtil.create(feedCriteria));
                }
            }));
        }
        if (streamListDownload != null) {
            registerHandler(streamListDownload
                    .addClickHandler(new DownloadStreamClickHandler(this, streamListPresenter, true, dispatcher)));
        }
        if (streamRelationListDownload != null) {
            registerHandler(streamRelationListDownload.addClickHandler(
                    new DownloadStreamClickHandler(this, streamRelationListPresenter, false, dispatcher)));
        }
        // Delete
        if (streamListDelete != null) {
            registerHandler(streamListDelete
                    .addClickHandler(new DeleteStreamClickHandler(this, streamListPresenter, true, dispatcher)));
        }
        if (streamRelationListDelete != null) {
            registerHandler(streamRelationListDelete.addClickHandler(
                    new DeleteStreamClickHandler(this, streamRelationListPresenter, false, dispatcher)));
        }
        // UN-Delete
        if (streamListUndelete != null) {
            registerHandler(streamListUndelete
                    .addClickHandler(new DeleteStreamClickHandler(this, streamListPresenter, true, dispatcher)));
        }
        if (streamRelationListUndelete != null) {
            registerHandler(streamRelationListUndelete.addClickHandler(
                    new DeleteStreamClickHandler(this, streamRelationListPresenter, false, dispatcher)));
        }
        // Process
        if (streamListProcess != null) {
            registerHandler(streamListProcess
                    .addClickHandler(new ProcessStreamClickHandler(this, streamListPresenter, true, dispatcher)));
        }
        if (streamRelationListProcess != null) {
            registerHandler(streamRelationListProcess.addClickHandler(
                    new ProcessStreamClickHandler(this, streamRelationListPresenter, false, dispatcher)));
        }
    }

    public boolean hasAdvancedCriteria(final FindStreamAttributeMapCriteria criteria) {
        if (!StreamStatus.UNLOCKED.equals(criteria.getFindStreamCriteria().obtainStatusSet().getSingleItem())) {
            return true;
        }
        return criteria.getFindStreamCriteria().obtainStatusPeriod().isConstrained();

    }

    private void showData() {
        final Stream stream = getSelectedStream();
        if (stream == null) {
            dataPresenter.clear();
        } else {
            dataPresenter.fetchData(stream);
        }
    }

    public void refresh() {
        // Get a new list of streams.
        streamListPresenter.refresh();
    }

    public FindStreamCriteria getCriteria() {
        return findStreamAttributeMapCriteria.getFindStreamCriteria();
    }

    @Override
    public void read(final BaseEntity entity) {
        if (entity instanceof Folder) {
            setFolderCriteria((Folder) entity);
        } else if (entity instanceof Feed) {
            setFeedCriteria((Feed) entity);
        } else if (entity instanceof PipelineEntity) {
            setPipelineCriteria((PipelineEntity) entity);
        } else {
            setNullCriteria();
        }
    }

    private FindStreamAttributeMapCriteria createFindStreamAttributeMapCriteria() {
        final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);

        final PageRequest pageRequest = criteria.obtainPageRequest();
        pageRequest.setLength(PageRequest.DEFAULT_PAGE_SIZE);
        pageRequest.setOffset(0L);

        criteria.obtainFindStreamCriteria().getFetchSet().add(Feed.ENTITY_TYPE);
        criteria.obtainFindStreamCriteria().getFetchSet().add(PipelineEntity.ENTITY_TYPE);
        criteria.obtainFindStreamCriteria().getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.obtainFindStreamCriteria().getFetchSet().add(StreamType.ENTITY_TYPE);
        criteria.obtainFindStreamCriteria().setOrderBy(FindStreamCriteria.ORDER_BY_CREATE_MS,
                OrderByDirection.DESCENDING);

        return criteria;
    }

    private void setFolderCriteria(final Folder folder) {
        showStreamListButtons(false);
        showStreamRelationListButtons(false);
        findStreamAttributeMapCriteria = createFindStreamAttributeMapCriteria();
        if (folder != null) {
            findStreamAttributeMapCriteria.obtainFindStreamCriteria().obtainFolderIdSet().add(folder);
        }

        this.folderVisible = false;
        this.feedVisible = false;
        this.pipelineVisible = true;

        initCriteria();
    }

    private void setFeedCriteria(final Feed feed) {
        feedCriteria = feed;
        showStreamListButtons(true);
        showStreamRelationListButtons(true);

        findStreamAttributeMapCriteria = createFindStreamAttributeMapCriteria();
        findStreamAttributeMapCriteria.obtainFindStreamCriteria().obtainFeeds().obtainInclude().add(feed);

        this.folderVisible = false;
        this.feedVisible = false;
        this.pipelineVisible = true;

        initCriteria();
    }

    private void setPipelineCriteria(final PipelineEntity pipelineEntity) {
        showStreamListButtons(false);
        showStreamRelationListButtons(false);

        findStreamAttributeMapCriteria = createFindStreamAttributeMapCriteria();
        findStreamAttributeMapCriteria.obtainFindStreamCriteria().obtainPipelineIdSet().add(pipelineEntity);

        // As this is for a pipeline then we will show processed output.
        final FindStreamCriteria findStreamCriteria = findStreamAttributeMapCriteria.obtainFindStreamCriteria();
        findStreamCriteria.obtainStreamTypeIdSet().clear();

        this.folderVisible = true;
        this.feedVisible = true;
        this.pipelineVisible = false;

        initCriteria();
    }

    private void setNullCriteria() {
        showStreamListButtons(false);
        showStreamRelationListButtons(false);
        findStreamAttributeMapCriteria = createFindStreamAttributeMapCriteria();

        this.folderVisible = true;
        this.feedVisible = true;
        this.pipelineVisible = true;

        initCriteria();
    }

    private void initCriteria() {
        streamListPresenter.setCriteria(findStreamAttributeMapCriteria);
        streamRelationListPresenter.setCriteria(null);
    }

    private Stream getSelectedStream() {
        StreamAttributeMap selectedStream = streamListPresenter.getSelectedStream();
        if (streamRelationListPresenter.getSelectedStream() != null) {
            selectedStream = streamRelationListPresenter.getSelectedStream();
        }

        if (selectedStream != null) {
            return selectedStream.getStream();
        }

        return null;
    }

    public EntityIdSet<Stream> getSelectedEntityIdSet() {
        return streamListPresenter.getSelectedEntityIdSet();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<EntityIdSet<Stream>> handler) {
        return streamListPresenter.addDataSelectionHandler(handler);
    }

    private void showStreamListButtons(final boolean visible) {
        if (streamListUpload != null) {
            streamListUpload.setVisible(visible);
        }
    }

    private void showStreamRelationListButtons(final boolean visible) {
    }

    public boolean isSomeSelected(final AbstractStreamListPresenter streamListPresenter,
                                  final EntityIdSet<Stream> selectedIdSet) {
        if (streamListPresenter.getResultList() == null || streamListPresenter.getResultList().size() == 0) {
            return false;
        }
        return !(selectedIdSet == null || selectedIdSet.isMatchNothing());
    }

    public void setStreamListSelectableEnabled(final EntityIdSet<Stream> streamIdSet, final StreamStatus streamStatus) {
        final boolean someSelected = isSomeSelected(streamListPresenter, streamIdSet);

        if (streamListDownload != null) {
            streamListDownload.setEnabled(someSelected);
        }
        if (streamListDelete != null) {
            streamListDelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getCriteria().obtainStatusSet().getSingleItem(),
                            streamListPresenter, streamIdSet, StreamStatus.LOCKED, StreamStatus.UNLOCKED));
        }
        if (streamListProcess != null) {
            streamListProcess.setEnabled(someSelected);
        }
        if (streamListUndelete != null) {
            // Hide if we are normal view (Unlocked streams)
            streamListUndelete.setVisible(!StreamStatus.UNLOCKED.equals(streamStatus));
            streamListUndelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getCriteria().obtainStatusSet().getSingleItem(),
                            streamListPresenter, streamIdSet, StreamStatus.DELETED));
        }

    }

    public void setStreamRelationListSelectableEnabled(final EntityIdSet<Stream> streamIdSet,
                                                       final StreamStatus streamStatus) {
        final boolean someSelected = isSomeSelected(streamRelationListPresenter, streamIdSet);

        if (streamRelationListDownload != null) {
            streamRelationListDownload.setEnabled(someSelected);
        }
        if (streamRelationListDelete != null) {
            streamRelationListDelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getCriteria().obtainStatusSet().getSingleItem(),
                            streamRelationListPresenter, streamIdSet, StreamStatus.LOCKED, StreamStatus.UNLOCKED));
        }
        if (streamRelationListUndelete != null) {
            // Hide if we are normal view (Unlocked streams)
            streamRelationListUndelete.setVisible(!StreamStatus.UNLOCKED.equals(streamStatus));
            streamRelationListUndelete
                    .setEnabled(someSelected && isSelectedAllOfStatus(getCriteria().obtainStatusSet().getSingleItem(),
                            streamRelationListPresenter, streamIdSet, StreamStatus.DELETED));
        }
        if (streamRelationListProcess != null) {
            streamRelationListProcess.setEnabled(someSelected);
        }
    }

    @Override
    public void beginStepping(final Long streamId, final StreamType childStreamType) {
        if (streamId != null) {
            // Try and get a pipeline id to use as a starting point for
            // stepping.
            SharedDocRef pipelineRef = null;

            // TODO : Fix by making entity id sets docref sets.
//            final EntityIdSet<PipelineEntity> entityIdSet = findStreamAttributeMapCriteria.obtainFindStreamCriteria()
//                    .getPipelineIdSet();
//            if (entityIdSet != null) {
//                if (entityIdSet.getSet().size() > 0) {
//                    pipelineRef = DocRefUtil.create(entityIdSet.getSet().iterator().next());
//                }
//            }

            // We will assume that the stream list has a child stream selected.
            // This would be the case where a user chooses an event with errors
            // in the top screen and then chooses the raw stream in the middle
            // pane to step through.
            Long childStreamId = null;
            final StreamAttributeMap map = streamListPresenter.getSelectedStream();
            if (map != null && map.getStream() != null) {
                final Stream childStream = map.getStream();
                // If the top list has a raw stream selected or isn't a child of
                // the selected stream then this is't the child stream we are
                // looking for.
                if (childStream.getParentStreamId() != null && childStream.getParentStreamId().equals(streamId)) {
                    childStreamId = childStream.getId();
                }
            }

            BeginPipelineSteppingEvent.fire(this, streamId, 0L, childStreamId, childStreamType, pipelineRef);
        }
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        dataPresenter.setClassificationUiHandlers(classificationUiHandlers);
    }

    public interface Resources extends ClientBundle {
        ImageResource pipeline();

        ImageResource pipelineDisabled();
    }

    public interface StreamView extends View {
    }

    private static abstract class AbstractStreamClickHandler implements ClickHandler, HasHandlers {
        private final StreamPresenter streamPresenter;
        private final AbstractStreamListPresenter streamListPresenter;
        private final boolean useCriteria;
        private final ClientDispatchAsync dispatcher;

        public AbstractStreamClickHandler(final StreamPresenter streamPresenter,
                                          final AbstractStreamListPresenter streamListPresenter, final boolean useCriteria,
                                          final ClientDispatchAsync dispatcher) {
            this.streamPresenter = streamPresenter;
            this.streamListPresenter = streamListPresenter;
            this.useCriteria = useCriteria;
            this.dispatcher = dispatcher;
        }

        protected AbstractStreamListPresenter getStreamListPresenter() {
            return streamListPresenter;
        }

        protected FindStreamCriteria createCriteria() {
            final EntityIdSet<Stream> idSet = streamListPresenter.getSelectedEntityIdSet();
            // First make sure there is some sort of selection, either
            // individual streams have been selected or all streams have been
            // selected.
            if (Boolean.TRUE.equals(idSet.getMatchAll()) || idSet.size() > 0) {
                // Only use match all if we are allowed to use criteria.
                if (useCriteria && Boolean.TRUE.equals(idSet.getMatchAll())) {
                    final FindStreamCriteria criteria = createFindStreamCriteria();
                    criteria.copyFrom(streamPresenter.getCriteria());
                    // Paging is NA
                    criteria.obtainPageRequest().setLength(null);
                    criteria.obtainPageRequest().setOffset(null);
                    return criteria;

                } else if (idSet.size() > 0) {
                    // If we aren't matching all then create a criteria that
                    // only includes the selected streams.
                    final FindStreamCriteria criteria = createFindStreamCriteria();
                    criteria.obtainStreamIdSet().addAll(idSet.getSet());
                    // Copy the current filter status
                    criteria.obtainStatusSet().copyFrom(streamPresenter.getCriteria().getStatusSet());
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
                final FindStreamCriteria criteria = createCriteria();
                if (criteria != null) {
                    performAction(criteria, dispatcher);
                }
            }
        }

        protected abstract void performAction(FindStreamCriteria criteria, ClientDispatchAsync dispatcher);

        @Override
        public void fireEvent(final GwtEvent<?> event) {
            streamPresenter.fireEvent(event);
        }

        protected void refreshList() {
            streamListPresenter.refresh();
        }
    }

    private static class DownloadStreamClickHandler extends AbstractStreamClickHandler {
        public DownloadStreamClickHandler(final StreamPresenter streamPresenter,
                                          final AbstractStreamListPresenter streamListPresenter, final boolean useCriteria,
                                          final ClientDispatchAsync dispatcher) {
            super(streamPresenter, streamListPresenter, useCriteria, dispatcher);
        }

        @Override
        protected void performAction(final FindStreamCriteria criteria, final ClientDispatchAsync dispatcher) {
            dispatcher.execute(new DownloadDataAction(criteria), new ExportFileCompleteHandler(null));
        }
    }

    private static class DeleteStreamClickHandler extends AbstractStreamClickHandler {
        public DeleteStreamClickHandler(final StreamPresenter streamPresenter,
                                        final AbstractStreamListPresenter streamListPresenter, final boolean useCriteria,
                                        final ClientDispatchAsync dispatcher) {
            super(streamPresenter, streamListPresenter, useCriteria, dispatcher);
        }

        protected String getDeleteText(final FindStreamCriteria criteria) {
            if (StreamStatus.DELETED.equals(criteria.obtainStatusSet().getSingleItem())) {
                return "Un-delete";
            } else {
                return "Delete";
            }
        }

        @Override
        protected void performAction(final FindStreamCriteria initialCriteria, final ClientDispatchAsync dispatcher) {
            final FindStreamCriteria deleteCriteria = new FindStreamCriteria();
            deleteCriteria.copyFrom(initialCriteria);

            if (deleteCriteria.obtainStatusSet().getSingleItem() == null) {
                for (final StreamStatus streamStatusToCheck : StreamStatus.values()) {
                    if (isSelectedAllOfStatus(null, getStreamListPresenter(), deleteCriteria.getStreamIdSet(),
                            streamStatusToCheck)) {
                        deleteCriteria.obtainStatusSet().setSingleItem(streamStatusToCheck);
                    }
                }
            }
            if (deleteCriteria.obtainStatusSet().getSingleItem() == null) {
                AlertEvent.fireError(this, "Unable to action command on mixed status", null);
            } else {
                ConfirmEvent.fire(this,
                        "Are you sure you want to " + getDeleteText(deleteCriteria) + " the selected items?",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean confirm) {
                                if (confirm) {
                                    if (!deleteCriteria.getStreamIdSet().isConstrained()) {
                                        ConfirmEvent.fireWarn(DeleteStreamClickHandler.this,
                                                "You have selected all items.  Are you sure you want to "
                                                        + getDeleteText(deleteCriteria) + " all the selected items?",
                                                new ConfirmCallback() {
                                                    @Override
                                                    public void onResult(final boolean confirm) {
                                                        if (confirm) {
                                                            doDelete(deleteCriteria, dispatcher);
                                                        }
                                                    }
                                                });

                                    } else {
                                        doDelete(deleteCriteria, dispatcher);
                                    }
                                }
                            }

                        });
            }
        }

        void doDelete(final FindStreamCriteria criteria, final ClientDispatchAsync dispatcher) {
            dispatcher.execute(new EntityServiceFindDeleteAction<FindStreamCriteria, Stream>(criteria),
                    new AsyncCallbackAdaptor<SharedLong>() {
                        @Override
                        public void onSuccess(final SharedLong result) {
                            AlertEvent.fireInfo(DeleteStreamClickHandler.this,
                                    getDeleteText(criteria) + " " + result + " records", new AlertCallback() {
                                        @Override
                                        public void onClose() {
                                            refreshList();
                                        }
                                    });
                        }
                    });
        }

    }

    private static class ProcessStreamClickHandler extends AbstractStreamClickHandler {
        public ProcessStreamClickHandler(final StreamPresenter streamPresenter,
                                         final AbstractStreamListPresenter streamListPresenter, final boolean useCriteria,
                                         final ClientDispatchAsync dispatcher) {
            super(streamPresenter, streamListPresenter, useCriteria, dispatcher);
        }

        @Override
        protected void performAction(final FindStreamCriteria criteria, final ClientDispatchAsync dispatcher) {
            if (criteria != null) {
                ConfirmEvent.fire(this, "Are you sure you want to reprocess the selected items", new ConfirmCallback() {
                    @Override
                    public void onResult(final boolean confirm) {
                        if (confirm) {
                            dispatcher.execute(new ReprocessDataAction(criteria),
                                    new AsyncCallbackAdaptor<SharedList<ReprocessDataInfo>>() {
                                        @Override
                                        public void onSuccess(final SharedList<ReprocessDataInfo> result) {
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
                                        }
                                    });
                        }
                    }
                });
            }
        }
    }
}
