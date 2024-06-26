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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced than find by
 * example.
 * </p>
 */
@XmlRootElement
@Deprecated
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
    private EntityIdSet<Feed> feedIdSet;

    private IncludeExcludeEntityIdSet<Feed> feeds;
    private EntityIdSet<PipelineEntity> pipelineIdSet;
    private EntityIdSet<StreamType> streamTypeIdSet;
    private EntityIdSet<Stream> streamIdSet;
    private CriteriaSet<StreamStatus> statusSet;
    private IdRange streamIdRange;
    private EntityIdSet<Stream> parentStreamIdSet;
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

    public IncludeExcludeEntityIdSet<Feed> getFeeds() {
        if (feedIdSet != null) {
            feeds = new IncludeExcludeEntityIdSet<>();
            feeds.setInclude(feedIdSet);
            this.feedIdSet = null;
        }

        return feeds;
    }

    public void setFeeds(final IncludeExcludeEntityIdSet<Feed> feeds) {
        this.feeds = feeds;
    }

    public IncludeExcludeEntityIdSet<Feed> obtainFeeds() {
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
    public EntityIdSet<Feed> getFeedIdSet() {
        return feedIdSet;
    }

    /**
     * You must use setFeeds() instead this is here for compatibility with
     * previous versions.
     **/
    @Deprecated
    public void setFeedIdSet(final EntityIdSet<Feed> feedIdSet) {
        if (feedIdSet != null) {
            feeds = new IncludeExcludeEntityIdSet<>();
            feeds.setInclude(feedIdSet);
        }
        this.feedIdSet = null;
    }

    public EntityIdSet<PipelineEntity> getPipelineIdSet() {
        return pipelineIdSet;
    }

    public void setPipelineIdSet(final EntityIdSet<PipelineEntity> pipelineIdSet) {
        this.pipelineIdSet = pipelineIdSet;
    }

    public EntityIdSet<PipelineEntity> obtainPipelineIdSet() {
        if (pipelineIdSet == null) {
            pipelineIdSet = new EntityIdSet<>();
        }
        return pipelineIdSet;
    }

    public EntityIdSet<StreamType> getStreamTypeIdSet() {
        return streamTypeIdSet;
    }

    public void setStreamTypeIdSet(final EntityIdSet<StreamType> streamTypeIdSet) {
        this.streamTypeIdSet = streamTypeIdSet;
    }

    public EntityIdSet<StreamType> obtainStreamTypeIdSet() {
        if (streamTypeIdSet == null) {
            streamTypeIdSet = new EntityIdSet<>();
        }
        return streamTypeIdSet;
    }

    public EntityIdSet<Stream> getStreamIdSet() {
        return streamIdSet;
    }

    public void setStreamIdSet(final EntityIdSet<Stream> streamIdSet) {
        this.streamIdSet = streamIdSet;
    }

    public EntityIdSet<Stream> obtainStreamIdSet() {
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

    public EntityIdSet<Stream> getParentStreamIdSet() {
        return parentStreamIdSet;
    }

    public void setParentStreamIdSet(final EntityIdSet<Stream> parentStreamIdSet) {
        this.parentStreamIdSet = parentStreamIdSet;
    }

    public EntityIdSet<Stream> obtainParentStreamIdSet() {
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
