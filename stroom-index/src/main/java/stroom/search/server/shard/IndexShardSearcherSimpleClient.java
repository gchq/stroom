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

package stroom.search.server.shard;

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import stroom.index.server.IndexShardUtil;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.query.shared.IndexConstants;
import stroom.search.server.SimpleCollector;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.util.AbstractCommandLineTool;
import stroom.util.io.StreamUtil;

public class IndexShardSearcherSimpleClient extends AbstractCommandLineTool {
    private String searchField = null;
    private String searchValue = null;

    public void setSearchField(final String searchField) {
        this.searchField = searchField;
    }

    public void setSearchValue(final String searchValue) {
        this.searchValue = searchValue;
    }

    @Override
    public void run() {
        // Boot up spring
        final ApplicationContext appContext = new ClassPathXmlApplicationContext(
                new String[] { "classpath:META-INF/spring/stroomCoreServerContext.xml" });

        final Query query = new TermQuery(new Term(searchField, searchValue));

        final IndexShardService indexShardService = appContext.getBean(IndexShardService.class);
        final StreamStore streamStore = appContext.getBean(StreamStore.class);

        final FindIndexShardCriteria findIndexShardCriteria = new FindIndexShardCriteria();
        findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.READABLE_INDEX_SHARD_STATUS);
        final List<IndexShard> indexShardList = indexShardService.find(findIndexShardCriteria);

        for (final IndexShard indexShard : indexShardList) {
            try {
                final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(indexShard);
                System.out.println("");
                System.out.println("Searching Index " + IndexShardUtil.getIndexDir(indexShard));
                final SimpleCollector simpleCollector = new SimpleCollector();
                final IndexReader reader = indexShardSearcher.getReader();
                final IndexSearcher searcher = new IndexSearcher(reader);
                searcher.search(query, simpleCollector);
                for (final Integer doc : simpleCollector.getDocIdList()) {
                    System.out.println("\tFound match " + doc);
                    final Document document = reader.document(doc);
                    for (final IndexableField fieldable : document.getFields()) {
                        System.out.println("\t\t" + fieldable.name() + "=" + fieldable.stringValue());
                    }

                    final Long streamId = Long.valueOf(document.getField(IndexConstants.STREAM_ID).stringValue());
                    final Long segment = Long.valueOf(document.getField(IndexConstants.EVENT_ID).stringValue());

                    // Try and open the stream source - pnly open unlocked ones.
                    final StreamSource streamSource = streamStore.openStreamSource(streamId);
                    if (streamSource != null) {
                        final RASegmentInputStream inputStream = new RASegmentInputStream(streamSource);
                        inputStream.include(segment);
                        System.out.println("\t\t" + StreamUtil.streamToString(inputStream));
                        streamStore.closeStreamSource(streamSource);
                    }
                }

                if (simpleCollector.getDocIdList().size() == 0) {
                    System.out.println("\tNo Matches");
                }
                System.out.println("");
                indexShardSearcher.destroy();
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        new IndexShardSearcherSimpleClient().doMain(args);
    }
}
