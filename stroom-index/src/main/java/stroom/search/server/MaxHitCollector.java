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

package stroom.search.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

/**
 * Collect results until we hit the max.
 */
public class MaxHitCollector extends Collector {
    private final int maxHits;
    private final List<Integer> docIdList = new ArrayList<>(100);
    private int docBase;

    public MaxHitCollector(final int maxHits) {
        this.maxHits = maxHits;
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
    public boolean acceptsDocsOutOfOrder() {
        return true;
    }

    @Override
    public void setNextReader(final AtomicReaderContext context) throws IOException {
        this.docBase = context.docBase;
    }

    @Override
    public void setScorer(final Scorer scorer) throws IOException {
        // Ignore.
    }

    public List<Integer> getDocIdList() {
        return docIdList;
    }
}
