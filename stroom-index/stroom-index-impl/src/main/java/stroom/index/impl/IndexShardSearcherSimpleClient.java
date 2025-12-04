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

package stroom.index.impl;

import stroom.util.AbstractCommandLineTool;

public class IndexShardSearcherSimpleClient extends AbstractCommandLineTool {

    private String searchField = null;
    private String searchValue = null;

    public void setSearchField(final String searchField) {
        this.searchField = searchField;
    }

    public void setSearchValue(final String searchValue) {
        this.searchValue = searchValue;
    }

    public static void main(final String[] args) {
        new IndexShardSearcherSimpleClient().doMain(args);
    }

    @Override
    public void run() {
//        final Query query = new TermQuery(new Term(searchField, searchValue));
//
//        final IndexShardService indexShardService = appContext.getInstance(IndexShardService.class);
//        final StreamStore streamStore = appContext.getInstance(StreamStore.class);
//
//        final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria.matchAll();
//        findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.READABLE_INDEX_SHARD_STATUS);
//        final List<IndexShard> indexShardList = indexShardService.find(findIndexShardCriteria);
//
//        for (final IndexShard indexShard : indexShardList) {
//            try {
//                final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(indexShard);
//                System.out.println("");
//                System.out.println("Searching Index " + IndexShardUtil.getIndexPath(indexShard));
//                final MaxHitCollector docIdListCollector = new MaxHitCollector(Integer.MAX_VALUE);
//                final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
//                final IndexSearcher searcher = searcherManager.acquire();
//                try {
//                    searcher.search(query, docIdListCollector);
//                    for (final Integer docId : docIdListCollector.getDocIdList()) {
//                        System.out.println("\tFound match " + docId);
//                        final Document document = searcher.doc(docId);
//                        for (final IndexableField fieldable : document.getFields()) {
//                            System.out.println("\t\t" + fieldable.name() + "=" + fieldable.stringValue());
//                        }
//
//                        final Long streamId = Long.valueOf(document.getField(IndexConstants.ID).stringValue());
//                        final Long segment = Long.valueOf(document.getField(IndexConstants.EVENT_ID).stringValue());
//
//                        // Try and open the stream source - pnly open unlocked ones.
//                        final StreamSource streamSource = streamStore.openSource(streamId);
//                        if (streamSource != null) {
//                            final RASegmentInputStream inputStream = new RASegmentInputStream(streamSource);
//                            inputStream.include(segment);
//                            System.out.println("\t\t" + StreamUtil.streamToString(inputStream));
//                            streamStore.closeStreamSource(streamSource);
//                        }
//                    }
//
//                    if (docIdListCollector.getDocIdList().size() == 0) {
//                        System.out.println("\tNo Matches");
//                    }
//                    System.out.println("");
//                } finally {
//                    searcherManager.release(searcher);
//                }
//                indexShardSearcher.destroy();
//            } catch (final RuntimeException e) {
//                ex.printStackTrace();
//            }
//        }
    }
}
