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
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.Period;
import stroom.streamstore.shared.StreamAttributeCondition;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced than find by
 * example.
 * </p>
 */
@Deprecated
@XmlRootElement
public class OldFindStreamCriteria extends BaseCriteria {
//    public static final String FIELD_CREATE_MS = "Create";
//    private static final long serialVersionUID = -4777723504698304778L;
//    /**
//     * Keep up to date as it's used to cache SQL queries.
//     */
//    private EntityIdSet<Processor> streamProcessorIdSet;
//    private OldFolderIdSet folderIdSet;
//
//    /**
//     * You must use feeds instead this is here for compatibility with previous
//     * versions.
//     **/
//    @Deprecated
//    private EntityIdSet<OldFeed> feedIdSet;
//
//    private IncludeExcludeEntityIdSet<OldFeed> feeds;
//    private EntityIdSet<OldPipelineEntity> pipelineIdSet;
//    private EntityIdSet<StreamTypeEntity> streamTypeIdSet;
//    private EntityIdSet<StreamEntity> streamIdSet;
//    private CriteriaSet<StreamStatus> statusSet;
//    private IdRange streamIdRange;
//    private EntityIdSet<StreamEntity> parentStreamIdSet;
//    private Period createPeriod;
//    private Period effectivePeriod;
//    private Period statusPeriod;
//    private List<StreamAttributeCondition> attributeConditionList;
//    private QueryData queryData;
//
//    public OldFindStreamCriteria() {
//    }
//
//    public EntityIdSet<Processor> getStreamProcessorIdSet() {
//        return streamProcessorIdSet;
//    }
//
//    public void setStreamProcessorIdSet(final EntityIdSet<Processor> streamProcessorIdSet) {
//        this.streamProcessorIdSet = streamProcessorIdSet;
//    }
//
//    public EntityIdSet<Processor> obtainStreamProcessorIdSet() {
//        if (streamProcessorIdSet == null) {
//            streamProcessorIdSet = new EntityIdSet<>();
//        }
//        return streamProcessorIdSet;
//    }
//
//    public CriteriaSet<StreamStatus> getStatusSet() {
//        return statusSet;
//    }
//
//    public void setStatusSet(final CriteriaSet<StreamStatus> statusSet) {
//        this.statusSet = statusSet;
//    }
//
//    public CriteriaSet<StreamStatus> obtainStatusSet() {
//        if (statusSet == null) {
//            statusSet = new CriteriaSet<>();
//        }
//        return statusSet;
//    }
//
//    public OldFolderIdSet getFolderIdSet() {
//        return folderIdSet;
//    }
//
//    public void setFolderIdSet(final OldFolderIdSet folderIdSet) {
//        this.folderIdSet = folderIdSet;
//    }
//
    public OldFolderIdSet obtainFolderIdSet() {
        throw new RuntimeException("Unexpected call to obtainFolderIdSet");
    }
//
//    public IncludeExcludeEntityIdSet<OldFeed> getFeeds() {
//        if (feedIdSet != null) {
//            feeds = new IncludeExcludeEntityIdSet<>();
//            feeds.setInclude(feedIdSet);
//            this.feedIdSet = null;
//        }
//
//        return feeds;
//    }
//
//    public void setFeeds(final IncludeExcludeEntityIdSet<OldFeed> feeds) {
//        this.feeds = feeds;
//    }
//
    public IncludeExcludeEntityIdSet<BaseEntity> obtainFeeds() {
        throw new RuntimeException("Unexpected call to obtainFeeds");
    }
//
//    /**
//     * You must use getFeeds() instead this is here for compatibility with
//     * previous versions.
//     **/
//    @Deprecated
//    public EntityIdSet<OldFeed> getFeedIdSet() {
//        return feedIdSet;
//    }
//
//    /**
//     * You must use setFeeds() instead this is here for compatibility with
//     * previous versions.
//     **/
//    @Deprecated
//    public void setFeedIdSet(final EntityIdSet<OldFeed> feedIdSet) {
//        if (feedIdSet != null) {
//            feeds = new IncludeExcludeEntityIdSet<>();
//            feeds.setInclude(feedIdSet);
//        }
//        this.feedIdSet = null;
//    }
//
//    public EntityIdSet<OldPipelineEntity> getPipelineIdSet() {
//        return pipelineIdSet;
//    }
//
//    public void setPipelineIdSet(final EntityIdSet<OldPipelineEntity> pipelineIdSet) {
//        this.pipelineIdSet = pipelineIdSet;
//    }
//
    public EntityIdSet<BaseEntity> obtainPipelineIdSet() {
        throw new RuntimeException("Unexpected call to obtainPipelineIdSet");
    }
//
//    public EntityIdSet<StreamTypeEntity> getStreamTypeIdSet() {
//        return streamTypeIdSet;
//    }
//
//    public void setStreamTypeIdSet(final EntityIdSet<StreamTypeEntity> streamTypeIdSet) {
//        this.streamTypeIdSet = streamTypeIdSet;
//    }
//
    public EntityIdSet<BaseEntity> obtainStreamTypeIdSet() {
        throw new RuntimeException("Unexpected call to obtainStreamTypeIdSet");
    }
//
//    public EntityIdSet<StreamEntity> getStreamIdSet() {
//        return streamIdSet;
//    }
//
//    public void setStreamIdSet(final EntityIdSet<StreamEntity> streamIdSet) {
//        this.streamIdSet = streamIdSet;
//    }
//
    public EntityIdSet<BaseEntity> obtainStreamIdSet() {
        throw new RuntimeException("Unexpected call to obtainStreamIdSet");
    }
//
//    public IdRange getStreamIdRange() {
//        return streamIdRange;
//    }
//
//    public void setStreamIdRange(final IdRange streamIdRange) {
//        this.streamIdRange = streamIdRange;
//    }
//
    public IdRange obtainStreamIdRange() {
        throw new RuntimeException("Unexpected call to obtainStreamIdRange");
    }
//
//    public EntityIdSet<StreamEntity> getParentStreamIdSet() {
//        return parentStreamIdSet;
//    }
//
//    public void setParentStreamIdSet(final EntityIdSet<StreamEntity> parentStreamIdSet) {
//        this.parentStreamIdSet = parentStreamIdSet;
//    }
//
    public EntityIdSet<BaseEntity> obtainParentStreamIdSet() {
        throw new RuntimeException("Unexpected call to obtainParentStreamIdSet");
    }
//
//    public Period getCreatePeriod() {
//        return createPeriod;
//    }
//
//    public void setCreatePeriod(final Period createPeriod) {
//        this.createPeriod = createPeriod;
//    }
//
    public Period obtainCreatePeriod() {
        throw new RuntimeException("Unexpected call to obtainCreatePeriod");

    }
//
//    public Period getEffectivePeriod() {
//        return effectivePeriod;
//    }
//
//    public void setEffectivePeriod(final Period effectivePeriod) {
//        this.effectivePeriod = effectivePeriod;
//    }
//
    public Period obtainEffectivePeriod() {
        throw new RuntimeException("Unexpected call to obtainEffectivePeriod");
    }
//
//    public Period getStatusPeriod() {
//        return statusPeriod;
//    }
//
//    public void setStatusPeriod(final Period statusPeriod) {
//        this.statusPeriod = statusPeriod;
//    }
//
    public Period obtainStatusPeriod() {
        throw new RuntimeException("Unexpected call to obtainStatusPeriod");
    }
//
//    public List<StreamAttributeCondition> getAttributeConditionList() {
//        return attributeConditionList;
//    }
//
//    public void setAttributeConditionList(final List<StreamAttributeCondition> attributeConditionList) {
//        this.attributeConditionList = attributeConditionList;
//    }
//
    public List<StreamAttributeCondition> obtainAttributeConditionList() {
        throw new RuntimeException("Unexpected call to obtainAttributeConditionList");
    }
//
//    public QueryData getQueryData() {
//        return queryData;
//    }
//
//    public void setQueryData(final QueryData queryData) {
//        this.queryData = queryData;
//    }
}
