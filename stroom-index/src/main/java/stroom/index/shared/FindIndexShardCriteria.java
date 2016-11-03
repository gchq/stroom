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

import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityMatcher;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.Range;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;

public class FindIndexShardCriteria extends BaseCriteria implements EntityMatcher<IndexShard> {
    private static final long serialVersionUID = 3552286394659242683L;

    public static final OrderBy ORDER_BY_ID = new OrderBy("Id", "id", IndexShard.ID);
    public static final OrderBy ORDER_BY_PARTITION = new OrderBy("Partition", "partition", IndexShard.PARTITION);

    private Range<Integer> documentCountRange = new Range<Integer>();
    private EntityIdSet<Node> nodeIdSet = new EntityIdSet<Node>();
    private EntityIdSet<Volume> volumeIdSet = new EntityIdSet<Volume>();
    private EntityIdSet<Index> indexIdSet = new EntityIdSet<Index>();
    private EntityIdSet<IndexShard> indexShardSet = new EntityIdSet<IndexShard>();
    private CriteriaSet<IndexShardStatus> indexShardStatusSet = new CriteriaSet<IndexShardStatus>();

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
    }

    public CriteriaSet<IndexShardStatus> getIndexShardStatusSet() {
        return indexShardStatusSet;
    }

    public void setDocumentCountRange(Range<Integer> documentCountRange) {
        this.documentCountRange = documentCountRange;
    }

    public Range<Integer> getDocumentCountRange() {
        return documentCountRange;
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
        return indexShardStatusSet.isMatch(indexShard.getStatus());

    }
}
