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

package stroom.streamstore;

import stroom.docref.DocRef;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Copyable;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.Matcher;
import stroom.entity.shared.Period;
import stroom.pipeline.shared.PipelineDoc;
import stroom.streamstore.shared.Feed;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

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
public class OldFindStreamCriteria extends BaseCriteria
        implements Copyable<OldFindStreamCriteria>, HasIsConstrained, Matcher<Stream> {
    //    public static final String FIELD_CREATE_MS = "Create";
    private static final long serialVersionUID = -4777723504698304778L;
    /**
     * Keep up to date as it's used to cache SQL queries.
     */
    private EntityIdSet<StreamProcessor> streamProcessorIdSet;

    /**
     * You must use feeds instead this is here for compatibility with previous
     * versions.
     **/
    @Deprecated
    private EntityIdSet<Feed> feedIdSet;

    private IncludeExcludeEntityIdSet<Feed> feeds;
    private CriteriaSet<DocRef> pipelineSet;
    private EntityIdSet<StreamType> streamTypeIdSet;
    private EntityIdSet<Stream> streamIdSet;
    private CriteriaSet<StreamStatus> statusSet;
    private IdRange streamIdRange;
    private EntityIdSet<Stream> parentStreamIdSet;
    private Period createPeriod;
    private Period effectivePeriod;
    private Period statusPeriod;
    private List<StreamAttributeCondition> attributeConditionList;

    public OldFindStreamCriteria() {
    }

    @Override
    public boolean isMatch(final Stream stream) {
        if (streamProcessorIdSet != null) {
            if (!streamProcessorIdSet.isMatch(stream.getStreamProcessor())) {
                return false;
            }
        }
        if (feeds != null) {
            if (!feeds.isMatch(stream.getFeed())) {
                return false;
            }
        }
        if (pipelineSet != null) {
            if (!pipelineSet.isMatch(new DocRef(PipelineDoc.DOCUMENT_TYPE, stream.getStreamProcessor().getPipelineUuid()))) {
                return false;
            }
        }
        if (streamTypeIdSet != null) {
            if (!streamTypeIdSet.isMatch(stream.getStreamType())) {
                return false;
            }
        }
        if (streamIdSet != null) {
            if (!streamIdSet.isMatch(stream)) {
                return false;
            }
        }
        if (statusSet != null) {
            if (!statusSet.isMatch(stream.getStatus())) {
                return false;
            }
        }
        if (parentStreamIdSet != null) {
            if (!parentStreamIdSet.isMatch(stream.getParentStreamId())) {
                return false;
            }
        }
        if (createPeriod != null) {
            if (!createPeriod.isMatch(stream.getCreateMs())) {
                return false;
            }
        }
        if (effectivePeriod != null) {
            if (!effectivePeriod.isMatch(stream.getEffectiveMs())) {
                return false;
            }
        }
        if (statusPeriod != null) {
            if (!statusPeriod.isMatch(stream.getStatusMs())) {
                return false;
            }
        }
        if (streamIdRange != null) {
            if (!streamIdRange.isMatch(stream.getId())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isConstrained() {
        if (streamProcessorIdSet != null && streamProcessorIdSet.isConstrained()) {
            return true;
        }
        if (feeds != null && feeds.isConstrained()) {
            return true;
        }
        if (pipelineSet != null && pipelineSet.isConstrained()) {
            return true;
        }
        if (streamTypeIdSet != null && streamTypeIdSet.isConstrained()) {
            return true;
        }
        if (createPeriod != null && createPeriod.isConstrained()) {
            return true;
        }
        if (effectivePeriod != null && effectivePeriod.isConstrained()) {
            return true;
        }
        if (statusPeriod != null && statusPeriod.isConstrained()) {
            return true;
        }
        if (statusSet != null && statusSet.isConstrained()) {
            return true;
        }
        if (streamIdSet != null && streamIdSet.isConstrained()) {
            return true;
        }
        if (parentStreamIdSet != null && parentStreamIdSet.isConstrained()) {
            return true;
        }
        return streamIdRange != null && streamIdRange.isConstrained();

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

    public CriteriaSet<DocRef> getPipelineSet() {
        return pipelineSet;
    }

    public void setPipelineSet(final CriteriaSet<DocRef> pipelineSet) {
        this.pipelineSet = pipelineSet;
    }

    public CriteriaSet<DocRef> obtainPipelineSet() {
        if (pipelineSet == null) {
            pipelineSet = new CriteriaSet<>();
        }
        return pipelineSet;
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

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(streamProcessorIdSet);
        builder.append(feeds);
        builder.append(pipelineSet);
        builder.append(streamTypeIdSet);
        builder.append(streamIdSet);
        builder.append(statusSet);
        builder.append(streamIdRange);
        builder.append(parentStreamIdSet);
        builder.append(createPeriod);
        builder.append(effectivePeriod);
        builder.append(statusPeriod);
        builder.append(streamIdRange);
        builder.append(statusSet);
        builder.append(attributeConditionList);

        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof OldFindStreamCriteria)) {
            return false;
        }

        final OldFindStreamCriteria other = (OldFindStreamCriteria) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.streamProcessorIdSet, other.streamProcessorIdSet);
        builder.append(this.feeds, other.feeds);
        builder.append(this.pipelineSet, other.pipelineSet);
        builder.append(this.streamTypeIdSet, other.streamTypeIdSet);
        builder.append(this.streamIdSet, other.streamIdSet);
        builder.append(this.statusSet, other.statusSet);
        builder.append(this.streamIdRange, other.streamIdRange);
        builder.append(this.parentStreamIdSet, other.parentStreamIdSet);
        builder.append(this.createPeriod, other.createPeriod);
        builder.append(this.effectivePeriod, other.effectivePeriod);
        builder.append(this.statusPeriod, other.statusPeriod);
        builder.append(this.statusSet, other.statusSet);
        builder.append(this.streamIdRange, other.streamIdRange);
        builder.append(this.attributeConditionList, other.attributeConditionList);

        return builder.isEquals();
    }

    @Override
    public void copyFrom(final OldFindStreamCriteria other) {
        this.obtainStreamProcessorIdSet().copyFrom(other.obtainStreamProcessorIdSet());
        this.obtainFeeds().copyFrom(other.obtainFeeds());
        this.obtainPipelineSet().copyFrom(other.obtainPipelineSet());
        this.obtainStreamTypeIdSet().copyFrom(other.obtainStreamTypeIdSet());
        this.obtainStreamIdSet().copyFrom(other.obtainStreamIdSet());
        this.obtainStatusSet().copyFrom(other.obtainStatusSet());
        this.obtainStreamIdRange().copyFrom(other.obtainStreamIdRange());
        this.obtainParentStreamIdSet().copyFrom(other.obtainParentStreamIdSet());
        this.createPeriod = Period.clone(other.createPeriod);
        this.effectivePeriod = Period.clone(other.effectivePeriod);
        this.statusPeriod = Period.clone(other.statusPeriod);

        if (other.attributeConditionList == null) {
            this.attributeConditionList = null;
        } else {
            this.attributeConditionList = new ArrayList<>(other.attributeConditionList);
        }

        super.copyFrom(other);
    }
}
