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

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.Folder;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.item.client.ItemListBox;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.widget.customdatebox.client.DateBoxView;

public class StreamFilterPresenter extends MyPresenterWidget<StreamFilterPresenter.StreamFilterView> {
    private final EntityIdSetPresenter folderPresenter;
    private final IncludeExcludeEntityIdSetPresenter<Feed> feedPresenter;
    private final EntityIdSetPresenter pipelinePresenter;
    private final EntityIdSetPresenter streamTypePresenter;
    private final ClientSecurityContext securityContext;
    private final StreamAttributeListPresenter streamAttributeListPresenter;
    private FindStreamAttributeMapCriteria criteria;

    @Inject
    public StreamFilterPresenter(final EventBus eventBus, final ClientSecurityContext securityContext,
                                 final EntityIdSetPresenter folderPresenter, final IncludeExcludeEntityIdSetPresenter<Feed> feedPresenter,
                                 final EntityIdSetPresenter pipelinePresenter, final EntityIdSetPresenter streamTypePresenter,
                                 final StreamAttributeListPresenter streamAttributeListPresenter,
                                 final StreamTypeUiManager streamTypeUiManager, final StreamFilterView view,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.securityContext = securityContext;
        this.folderPresenter = folderPresenter;
        this.feedPresenter = feedPresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.streamTypePresenter = streamTypePresenter;
        this.streamAttributeListPresenter = streamAttributeListPresenter;

        view.setFolderView(folderPresenter.getView());
        view.setFeedView(feedPresenter.getView());
        view.setPipelineView(pipelinePresenter.getView());
        view.setStreamTypeView(streamTypePresenter.getView());
        view.setStreamAttributeListView(streamAttributeListPresenter.getView());

        view.getStreamStatus().addItems(StreamStatus.values());

        view.getStreamListFilterTemplate().addSelectionHandler(new SelectionHandler<StreamListFilterTemplate>() {
            @Override
            public void onSelection(final SelectionEvent<StreamListFilterTemplate> event) {
                final StreamListFilterTemplate template = event.getSelectedItem();
                if (template != null) {
                    final Period period = new Period();
                    period.setFromMs(System.currentTimeMillis() - template.getHourPeriod() * 60 * 60 * 1000);
                    criteria.obtainFindStreamCriteria().setCreatePeriod(period);
                    criteria.obtainFindStreamCriteria().obtainStreamTypeIdSet().clear();

                    criteria.obtainFindStreamCriteria().obtainStreamTypeIdSet()
                            .addAllEntities(template.getStreamType(streamTypeUiManager));
                    read();
                }

            }
        });

    }

    public static void stringToIdSet(final String val, final EntityIdSet<?> idSet) {
        idSet.clear();
        if (val.length() > 0) {
            final String[] arr = val.split(",");
            for (final String v : arr) {
                try {
                    final Long id = Long.valueOf(v.trim());
                    idSet.add(id);
                } catch (final NumberFormatException e) {
                    // Ignore.
                }
            }
        }
    }

    public static String idSetToString(final EntityIdSet<?> idSet) {
        if (idSet == null || idSet.size() == 0) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final Long id : idSet) {
            sb.append(id.toString());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public FindStreamAttributeMapCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(final FindStreamAttributeMapCriteria criteria, final boolean folderEnabled,
                            final boolean feedEnabled, final boolean pipelineEnabled, final boolean attributesEnabled,
                            final boolean advancedVisable) {
        this.criteria = new FindStreamAttributeMapCriteria();
        this.criteria.copyFrom(criteria);

        folderPresenter.setEnabled(folderEnabled);

        //if (securityContext.hasAppPermission(Feed.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        feedPresenter.setEnabled(feedEnabled);
        getView().setFeedVisible(true);
//        } else {
//            feedPresenter.setEnabled(false);
//            getView().setFeedVisible(false);
//        }

        //if (securityContext.hasAppPermission(PipelineEntity.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        pipelinePresenter.setEnabled(pipelineEnabled);
        getView().setPipelineVisible(true);
//        } else {
//            pipelinePresenter.setEnabled(false);
//            getView().setPipelineVisible(false);
//        }

        folderPresenter.setEnabled(folderEnabled);

        getView().setStreamAttributeListVisible(true);
        getView().setAdvancedVisible(advancedVisable);
        read();
    }

    private void read() {
        final FindStreamCriteria findStreamCriteria = criteria.obtainFindStreamCriteria();
        final EntityIdSet<Folder> folderIdSet = findStreamCriteria.obtainFolderIdSet();

        folderPresenter.read(Folder.ENTITY_TYPE, true, folderIdSet);
        final IncludeExcludeEntityIdSet<Feed> feeds = findStreamCriteria.obtainFeeds();
        feedPresenter.read(Feed.ENTITY_TYPE, true, feeds);
        final EntityIdSet<PipelineEntity> pipelineIdSet = findStreamCriteria.obtainPipelineIdSet();
        pipelinePresenter.read(PipelineEntity.ENTITY_TYPE, true, pipelineIdSet);
        final EntityIdSet<StreamType> streamTypeIdSet = findStreamCriteria.obtainStreamTypeIdSet();
        streamTypePresenter.read(StreamType.ENTITY_TYPE, false, streamTypeIdSet);

        getView().getCreateFrom().setMilliseconds(findStreamCriteria.obtainCreatePeriod().getFrom());
        getView().getCreateTo().setMilliseconds(findStreamCriteria.obtainCreatePeriod().getTo());
        getView().getEffectiveFrom().setMilliseconds(findStreamCriteria.obtainEffectivePeriod().getFrom());
        getView().getEffectiveTo().setMilliseconds(findStreamCriteria.obtainEffectivePeriod().getTo());

        getView().getStreamId().setText(idSetToString(findStreamCriteria.obtainStreamIdSet()));
        getView().getParentStreamId().setText(idSetToString(findStreamCriteria.obtainParentStreamIdSet()));

        getView().getStreamStatus().setSelectedItem(findStreamCriteria.obtainStatusSet().getSingleItem());
        getView().getStatusFrom().setMilliseconds(findStreamCriteria.obtainStatusPeriod().getFrom());
        getView().getStatusTo().setMilliseconds(findStreamCriteria.obtainStatusPeriod().getTo());

        streamAttributeListPresenter.read(findStreamCriteria.obtainAttributeConditionList());
    }

    public void write() {
        final FindStreamCriteria findStreamCriteria = criteria.obtainFindStreamCriteria();

        if (folderPresenter.isEnabled()) {
            final EntityIdSet<Folder> folderIdSet = findStreamCriteria.obtainFolderIdSet();
            folderIdSet.clear();
            folderPresenter.write(folderIdSet);
        }
        if (feedPresenter.isEnabled()) {
            final IncludeExcludeEntityIdSet<Feed> feeds = findStreamCriteria.obtainFeeds();
            feeds.clear();
            feedPresenter.write(feeds);
        }
        if (pipelinePresenter.isEnabled()) {
            final EntityIdSet<PipelineEntity> pipelineIdSet = findStreamCriteria.obtainPipelineIdSet();
            pipelineIdSet.clear();
            pipelinePresenter.write(pipelineIdSet);
        }
        final EntityIdSet<StreamType> streamTypeIdSet = findStreamCriteria.obtainStreamTypeIdSet();
        streamTypeIdSet.clear();
        streamTypePresenter.write(streamTypeIdSet);

        findStreamCriteria.setCreatePeriod(
                new Period(getView().getCreateFrom().getMilliseconds(), getView().getCreateTo().getMilliseconds()));
        findStreamCriteria.setEffectivePeriod(
                new Period(getView().getEffectiveFrom().getMilliseconds(), getView().getEffectiveTo().getMilliseconds()));

        stringToIdSet(getView().getStreamId().getText(), findStreamCriteria.obtainStreamIdSet());
        stringToIdSet(getView().getParentStreamId().getText(), findStreamCriteria.obtainParentStreamIdSet());

        if (getView().isAdvancedVisible()) {
            findStreamCriteria.obtainStatusSet().setSingleItem(getView().getStreamStatus().getSelectedItem());
            findStreamCriteria.setStatusPeriod(
                    new Period(getView().getStatusFrom().getMilliseconds(), getView().getStatusTo().getMilliseconds()));
        } else {
            findStreamCriteria.obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);
            findStreamCriteria.setStatusPeriod(null);
        }

        streamAttributeListPresenter.write(findStreamCriteria.obtainAttributeConditionList());

    }

    public interface StreamFilterView extends View {
        ItemListBox<StreamListFilterTemplate> getStreamListFilterTemplate();

        void setFolderView(View view);

        void setFolderVisible(boolean visible);

        void setFeedView(View view);

        void setFeedVisible(boolean visible);

        void setPipelineView(View view);

        void setPipelineVisible(boolean visible);

        void setStreamTypeView(View view);

        void setStreamAttributeListView(View view);

        void setStreamAttributeListVisible(boolean visible);

        DateBoxView getCreateFrom();

        DateBoxView getCreateTo();

        DateBoxView getEffectiveFrom();

        DateBoxView getEffectiveTo();

        HasText getStreamId();

        HasText getParentStreamId();

        boolean isAdvancedVisible();

        void setAdvancedVisible(boolean newVal);

        ItemListBox<StreamStatus> getStreamStatus();

        DateBoxView getStatusFrom();

        DateBoxView getStatusTo();
    }
}
