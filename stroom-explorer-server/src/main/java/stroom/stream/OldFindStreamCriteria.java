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

package stroom.stream;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.Period;
import stroom.pipeline.OldPipelineEntity;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.streamtask.shared.StreamProcessor;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced than find by
 * example.
 * </p>
 */
@XmlRootElement
public class OldFindStreamCriteria extends BaseCriteria {
    public static final String FIELD_CREATE_MS = "Create";
    private static final long serialVersionUID = -4777723504698304778L;
    /**
     * Keep up to date as it's used to cache SQL queries.
     */
    private EntityIdSet<StreamProcessor> streamProcessorIdSet;
    private OldFolderIdSet folderIdSet;

    /**
     * You must use feeds instead this is here for compatibility with previous
     * versions.
     **/
    @Deprecated
    private EntityIdSet<FeedEntity> feedIdSet;

    private IncludeExcludeEntityIdSet<FeedEntity> feeds;
    private EntityIdSet<OldPipelineEntity> pipelineIdSet;
    private EntityIdSet<StreamTypeEntity> streamTypeIdSet;
    private EntityIdSet<StreamEntity> streamIdSet;
    private CriteriaSet<StreamStatus> statusSet;
    private IdRange streamIdRange;
    private EntityIdSet<StreamEntity> parentStreamIdSet;
    private Period createPeriod;
    private Period effectivePeriod;
    private Period statusPeriod;
    private List<StreamAttributeCondition> attributeConditionList;
    private QueryData queryData;

    public OldFindStreamCriteria() {
    }

    public EntityIdSet<StreamProcessor> getStreamProcessorIdSet() {
        return streamProcessorIdSet;
    }

    public void setStreamProcessorIdSet(final EntityIdSet<StreamProcessor> streamProcessorIdSet) {
        this.streamProcessorIdSet = streamProcessorIdSet;
    }

    public EntityIdSet<StreamProcessor> obtainStreamProcessorIdSet() {
        if (streamProcessorIdSet == null) {
            streamProcessorIdSet = new EntityIdSet<>();
        }
        return streamProcessorIdSet;
    }

    public CriteriaSet<StreamStatus> getStatusSet() {
        return statusSet;
    }

    public void setStatusSet(final CriteriaSet<StreamStatus> statusSet) {
        this.statusSet = statusSet;
    }

    public CriteriaSet<StreamStatus> obtainStatusSet() {
        if (statusSet == null) {
            statusSet = new CriteriaSet<>();
        }
        return statusSet;
    }

    public OldFolderIdSet getFolderIdSet() {
        return folderIdSet;
    }

    public void setFolderIdSet(final OldFolderIdSet folderIdSet) {
        this.folderIdSet = folderIdSet;
    }

    public OldFolderIdSet obtainFolderIdSet() {
        if (folderIdSet == null) {
            folderIdSet = new OldFolderIdSet();
        }
        return folderIdSet;
    }

    public IncludeExcludeEntityIdSet<FeedEntity> getFeeds() {
        if (feedIdSet != null) {
            feeds = new IncludeExcludeEntityIdSet<>();
            feeds.setInclude(feedIdSet);
            this.feedIdSet = null;
        }

        return feeds;
    }

    public void setFeeds(final IncludeExcludeEntityIdSet<FeedEntity> feeds) {
        this.feeds = feeds;
    }

    public IncludeExcludeEntityIdSet<FeedEntity> obtainFeeds() {
        if (feedIdSet != null) {
            feeds = new IncludeExcludeEntityIdSet<>();
            feeds.setInclude(feedIdSet);
            this.feedIdSet = null;
        }

        if (feeds == null) {
            feeds = new IncludeExcludeEntityIdSet<>();
        }
        return feeds;
    }

    /**
     * You must use getFeeds() instead this is here for compatibility with
     * previous versions.
     **/
    @Deprecated
    public EntityIdSet<FeedEntity> getFeedIdSet() {
        return feedIdSet;
    }

    /**
     * You must use setFeeds() instead this is here for compatibility with
     * previous versions.
     **/
    @Deprecated
    public void setFeedIdSet(final EntityIdSet<FeedEntity> feedIdSet) {
        if (feedIdSet != null) {
            feeds = new IncludeExcludeEntityIdSet<>();
            feeds.setInclude(feedIdSet);
        }
        this.feedIdSet = null;
    }

    public EntityIdSet<OldPipelineEntity> getPipelineIdSet() {
        return pipelineIdSet;
    }

    public void setPipelineIdSet(final EntityIdSet<OldPipelineEntity> pipelineIdSet) {
        this.pipelineIdSet = pipelineIdSet;
    }

    public EntityIdSet<OldPipelineEntity> obtainPipelineIdSet() {
        if (pipelineIdSet == null) {
            pipelineIdSet = new EntityIdSet<>();
        }
        return pipelineIdSet;
    }

    public EntityIdSet<StreamTypeEntity> getStreamTypeIdSet() {
        return streamTypeIdSet;
    }

    public void setStreamTypeIdSet(final EntityIdSet<StreamTypeEntity> streamTypeIdSet) {
        this.streamTypeIdSet = streamTypeIdSet;
    }

    public EntityIdSet<StreamTypeEntity> obtainStreamTypeIdSet() {
        if (streamTypeIdSet == null) {
            streamTypeIdSet = new EntityIdSet<>();
        }
        return streamTypeIdSet;
    }

    public EntityIdSet<StreamEntity> getStreamIdSet() {
        return streamIdSet;
    }

    public void setStreamIdSet(final EntityIdSet<StreamEntity> streamIdSet) {
        this.streamIdSet = streamIdSet;
    }

    public EntityIdSet<StreamEntity> obtainStreamIdSet() {
        if (streamIdSet == null) {
            streamIdSet = new EntityIdSet<>();
        }
        return streamIdSet;
    }

    public IdRange getStreamIdRange() {
        return streamIdRange;
    }

    public void setStreamIdRange(final IdRange streamIdRange) {
        this.streamIdRange = streamIdRange;
    }

    public IdRange obtainStreamIdRange() {
        if (streamIdRange == null) {
            streamIdRange = new IdRange();
        }
        return streamIdRange;
    }

    public EntityIdSet<StreamEntity> getParentStreamIdSet() {
        return parentStreamIdSet;
    }

    public void setParentStreamIdSet(final EntityIdSet<StreamEntity> parentStreamIdSet) {
        this.parentStreamIdSet = parentStreamIdSet;
    }

    public EntityIdSet<StreamEntity> obtainParentStreamIdSet() {
        if (parentStreamIdSet == null) {
            parentStreamIdSet = new EntityIdSet<>();
        }
        return parentStreamIdSet;
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public void setCreatePeriod(final Period createPeriod) {
        this.createPeriod = createPeriod;
    }

    public Period obtainCreatePeriod() {
        if (createPeriod == null) {
            createPeriod = new Period();
        }
        return createPeriod;

    }

    public Period getEffectivePeriod() {
        return effectivePeriod;
    }

    public void setEffectivePeriod(final Period effectivePeriod) {
        this.effectivePeriod = effectivePeriod;
    }

    public Period obtainEffectivePeriod() {
        if (effectivePeriod == null) {
            effectivePeriod = new Period();
        }
        return effectivePeriod;
    }

    public Period getStatusPeriod() {
        return statusPeriod;
    }

    public void setStatusPeriod(final Period statusPeriod) {
        this.statusPeriod = statusPeriod;
    }

    public Period obtainStatusPeriod() {
        if (statusPeriod == null) {
            statusPeriod = new Period();
        }
        return statusPeriod;
    }

    public List<StreamAttributeCondition> getAttributeConditionList() {
        return attributeConditionList;
    }

    public void setAttributeConditionList(final List<StreamAttributeCondition> attributeConditionList) {
        this.attributeConditionList = attributeConditionList;
    }

    public List<StreamAttributeCondition> obtainAttributeConditionList() {
        if (attributeConditionList == null) {
            attributeConditionList = new ArrayList<>();
        }
        return attributeConditionList;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public void setQueryData(final QueryData queryData) {
        this.queryData = queryData;
    }
}
