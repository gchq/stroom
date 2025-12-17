/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.lucene;

import stroom.search.impl.SearchException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collect results until we hit the max.
 */
class MaxHitCollector extends SimpleCollector {

    private final int maxHits;
    private final List<Integer> docIdList = new ArrayList<>(100);
    private int docBase;

    MaxHitCollector(final int maxHits) {
        this.maxHits = maxHits;
    }

    @Override
    protected void doSetNextReader(final LeafReaderContext context) throws IOException {
        super.doSetNextReader(context);
        docBase = context.docBase;
    }

    @Override
    public void collect(final int doc) {
        final int docId = docBase + doc;
        docIdList.add(docId);

        if (docIdList.size() >= maxHits) {
            throw new SearchException("Max hits exceeded!");
        }
    }

    @Override
    public ScoreMode scoreMode() {
        return ScoreMode.COMPLETE_NO_SCORES;
    }

    public List<Integer> getDocIdList() {
        return docIdList;
    }
}
