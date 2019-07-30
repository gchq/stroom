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
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.Range;
import stroom.util.shared.StringCriteria;

public class FindIndexShardCriteria extends BaseCriteria {
    private static final long serialVersionUID = 3552286394659242683L;

    public static final String FIELD_ID = "Id";
    public static final String FIELD_PARTITION = "Partition";

    private Range<Integer> documentCountRange = new Range<>();
    private CriteriaSet<String> nodeNameSet = new CriteriaSet<>();
    private CriteriaSet<Integer> volumeIdSet = new CriteriaSet<>();
    private CriteriaSet<String> indexUuidSet = new CriteriaSet<>();
    private CriteriaSet<Long> indexShardIdSet = new CriteriaSet<>();
    private CriteriaSet<IndexShardStatus> indexShardStatusSet = new CriteriaSet<>();
    private StringCriteria partition = new StringCriteria();

    public FindIndexShardCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindIndexShardCriteria(final FindIndexShardCriteria criteria) {
        // Copy constructor.
        nodeNameSet.copyFrom(criteria.nodeNameSet);
        volumeIdSet.copyFrom(criteria.volumeIdSet);
        documentCountRange = criteria.documentCountRange;
        indexUuidSet.copyFrom(criteria.indexUuidSet);
        indexShardIdSet.copyFrom(criteria.indexShardIdSet);
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

    public CriteriaSet<String> getIndexUuidSet() {
        return indexUuidSet;
    }

    public CriteriaSet<Long> getIndexShardIdSet() {
        return indexShardIdSet;
    }

    public CriteriaSet<String> getNodeNameSet() {
        return nodeNameSet;
    }

    public CriteriaSet<Integer> getVolumeIdSet() {
        return volumeIdSet;
    }

    public StringCriteria getPartition() {
        return partition;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("indexUuidSet=");
        sb.append(indexUuidSet);
        return sb.toString();
    }
}
