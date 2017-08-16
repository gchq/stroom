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

package stroom.index.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityMatcher;
import stroom.entity.shared.Range;
import stroom.entity.shared.StringCriteria;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;

public class FindIndexShardCriteria extends BaseCriteria implements EntityMatcher<IndexShard> {
    public static final String FIELD_PARTITION = "Partition";
    private static final long serialVersionUID = 3552286394659242683L;
    private Range<Integer> documentCountRange = new Range<>();
    private EntityIdSet<Node> nodeIdSet = new EntityIdSet<>();
    private EntityIdSet<Volume> volumeIdSet = new EntityIdSet<>();
    private EntityIdSet<Index> indexIdSet = new EntityIdSet<>();
    private EntityIdSet<IndexShard> indexShardSet = new EntityIdSet<>();
    private CriteriaSet<IndexShardStatus> indexShardStatusSet = new CriteriaSet<>();
    private StringCriteria partition = new StringCriteria();

    public FindIndexShardCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindIndexShardCriteria(final FindIndexShardCriteria criteria) {
        // Copy constructor.
        nodeIdSet.copyFrom(criteria.nodeIdSet);
        volumeIdSet.copyFrom(criteria.volumeIdSet);
        documentCountRange = criteria.documentCountRange;
        indexIdSet.copyFrom(criteria.indexIdSet);
        indexShardSet.copyFrom(criteria.indexShardSet);
        indexShardStatusSet.copyFrom(criteria.indexShardStatusSet);
        partition.copyFrom(criteria.partition);
    }

    public CriteriaSet<IndexShardStatus> getIndexShardStatusSet() {
        return indexShardStatusSet;
    }

    public Range<Integer> getDocumentCountRange() {
        return documentCountRange;
    }

    public void setDocumentCountRange(Range<Integer> documentCountRange) {
        this.documentCountRange = documentCountRange;
    }

    public EntityIdSet<Index> getIndexIdSet() {
        return indexIdSet;
    }

    public EntityIdSet<IndexShard> getIndexShardSet() {
        return indexShardSet;
    }

    public EntityIdSet<Node> getNodeIdSet() {
        return nodeIdSet;
    }

    public EntityIdSet<Volume> getVolumeIdSet() {
        return volumeIdSet;
    }

    public StringCriteria getPartition() {
        return partition;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("indexIdSet=");
        sb.append(indexIdSet);
        return sb.toString();
    }

    @Override
    public boolean isMatch(final IndexShard indexShard) {
        if (!nodeIdSet.isMatch(indexShard.getNode())) {
            return false;
        }
        if (!volumeIdSet.isMatch(indexShard.getVolume())) {
            return false;
        }
        if (!indexIdSet.isMatch(indexShard.getIndex())) {
            return false;
        }
        if (!indexShardSet.isMatch(indexShard)) {
            return false;
        }
        if (!indexShardStatusSet.isMatch(indexShard.getStatus())) {
            return false;
        }
        return partition.isMatch(indexShard.getPartition());
    }
}
