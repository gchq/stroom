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

package stroom.index.server;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.node.shared.Volume;
import stroom.query.shared.IndexConstants;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexField.AnalyzerType;
import stroom.query.shared.IndexFields;
import stroom.search.server.IndexShardSearcherImpl;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.AbstractCommandLineTool;
import stroom.util.logging.LoggerPrintStream;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.ThreadUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class BenchmarkIndex extends AbstractCommandLineTool {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(BenchmarkIndex.class);
    private final List<String> docArgs = new ArrayList<>();
    private IndexShard[] indexShards;
    private IndexShardService indexShardService;
    private Long nextCommit = null;
    private IndexFields indexFields;

    // Our settings
    private long docCount = 50000;
    private Long commitCount = null;
    private int jobSize = 10000;
    private int docSize = 3;
    private int poolSize = 1;
    private int ramBufferMbSize = IndexShardWriterImpl.DEFAULT_RAM_BUFFER_MB_SIZE;
    private String path = System.getProperty("user.home") + "/tmp/benchmarkindex";

    public static int getRandomSkewed() {
        return (int) ((Math.exp(Math.random() * 10) * Math.exp(Math.random() * 10)) + Math.exp(Math.random() * 10));
    }

    public static void main(final String[] args) throws Exception {
        final BenchmarkIndex benchmarkIndex = new BenchmarkIndex();
        benchmarkIndex.doMain(args);
    }

    public void setDocCount(final long docCount) {
        this.docCount = docCount;
    }

    public void setCommitCount(final Long commitCount) {
        this.commitCount = commitCount;
    }

    public void setJobSize(final int jobSize) {
        this.jobSize = jobSize;
    }

    public void setDocSize(final int docSize) {
        this.docSize = docSize;
    }

    public void setPoolSize(final int poolSize) {
        this.poolSize = poolSize;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public void setRamBufferMbSize(final int ramBufferSize) {
        this.ramBufferMbSize = ramBufferSize;
    }

    private Document buildDocument(final long id) {
        final Document doc = new Document();

        final LongField streamId = FieldFactory.create(getIndexField(IndexConstants.STREAM_ID), id);
        final LongField eventId = FieldFactory.create(getIndexField(IndexConstants.EVENT_ID), id);

        doc.add(streamId);
        doc.add(eventId);

        for (final String arg : docArgs) {
            final IndexField indexField = getIndexField(arg);
            doc.add(FieldFactory.create(indexField, "user" + getRandomSkewed()));
        }

        final IndexField multifield = getIndexField("multifield");
        final IndexField dupfield = getIndexField("dupfield");

        doc.add(FieldFactory.create(multifield, "user" + getRandomSkewed()));
        doc.add(FieldFactory.create(dupfield, "user" + getRandomSkewed()));
        doc.add(FieldFactory.create(dupfield, "user" + getRandomSkewed()));

        return doc;
    }

    private IndexField getIndexField(final String name) {
        for (final IndexField indexField : indexFields.getIndexFields()) {
            if (indexField.getFieldName().equals(name)) {
                return indexField;
            }
        }
        return null;
    }

    private Document getDocument(final long id) {
        return buildDocument(id);
    }

    public void init() {
        final Index index = new Index();
        index.setName("Test index");

        final Volume volume = new Volume();
        volume.setPath(path);

        FileSystemUtil.deleteDirectory(new File(volume.getPath()));
        indexShards = new IndexShard[poolSize];
        for (int i = 0; i < indexShards.length; i++) {
            indexShards[i] = new IndexShard();
            indexShards[i].setIndex(index);
            indexShards[i].setPartition("all");
            indexShards[i].setId(Long.valueOf(i));
            indexShards[i].setVolume(volume);
        }

        if (commitCount != null) {
            nextCommit = commitCount;
        }

        for (int i = 0; i < docSize; i++) {
            docArgs.add("Arg" + i);
        }

        indexFields = IndexFields.createStreamIndexFields();

        for (final String arg : docArgs) {
            final IndexField indexField = IndexField.createField(arg, AnalyzerType.WHITESPACE, false, false, true,
                    false);
            indexFields.add(indexField);
        }

        indexFields.add(IndexField.createField("multifield", AnalyzerType.WHITESPACE, false, false, true, false));
        indexFields.add(IndexField.createField("dupfield", AnalyzerType.WHITESPACE, false, false, true, false));
    }

    @Override
    public void run() {
        init();

        final long batchStartTime = System.currentTimeMillis();

        final IndexShardWriterImpl[] writers = new IndexShardWriterImpl[indexShards.length];
        for (int i = 0; i < writers.length; i++) {
            final IndexShard indexShard = indexShards[i];
            writers[i] = new IndexShardWriterImpl(indexShardService, indexFields, indexShard.getIndex(), indexShard);
            writers[i].setRamBufferSizeMB(ramBufferMbSize);
            writers[i].open(true);
        }
        final AtomicLong atomicLong = new AtomicLong();

        final long indexStartTime = System.currentTimeMillis();

        final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(jobSize);
        for (int i = 0; i < jobSize; i++) {
            final Runnable r = () -> {
                long myId;
                while ((myId = atomicLong.incrementAndGet()) < docCount) {
                    try {
                        final int idx = (int) (myId % writers.length);
                        writers[idx].addDocument(getDocument(myId));
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            threadPoolExecutor.execute(r);
        }

        threadPoolExecutor.shutdown();

        // Wait for termination.
        while (!threadPoolExecutor.isTerminated()) {
            // Wait 1 second.
            ThreadUtil.sleep(1000);

            final long docsSoFar = atomicLong.get();
            final long secondsSoFar = (System.currentTimeMillis() - batchStartTime) / 1000;

            for (int i = 0; i < writers.length; i++) {
                final IndexShardWriterImpl impl = writers[i];
                final IndexShard indexShard = indexShards[i];

                if (secondsSoFar > 0) {
                    final long docsPerSecond = docsSoFar / secondsSoFar;
                    impl.sync();
                    LOGGER.info("run() - " + StringUtils.rightPad(ModelStringUtil.formatCsv(docsSoFar), 10) + " doc ps "
                            + ModelStringUtil.formatCsv(docsPerSecond) + " (" + indexShard.getFileSizeString() + ")");
                }
                if (nextCommit != null && docsSoFar > nextCommit) {
                    impl.flush();
                    nextCommit = ((docsSoFar / commitCount) * commitCount) + commitCount;
                    LOGGER.info("run() - commit " + docsSoFar + " next commit is " + nextCommit);
                }
            }
        }
        final long indexEndTime = System.currentTimeMillis();
        final long secondsSoFar = (System.currentTimeMillis() - batchStartTime) / 1000;
        final long docsPerSecond = atomicLong.get() / secondsSoFar;

        for (final IndexShardWriter writer : writers) {
            writer.close();
        }

        final long batchEndTime = System.currentTimeMillis();

        LOGGER.info("runWrite() - Complete");
        LOGGER.info("=====================");
        LOGGER.info("");
        LOGGER.info("Using Args");
        LOGGER.info("==========");
        LoggerPrintStream traceStream = LoggerPrintStream.create(LOGGER, false);
        traceArguments(traceStream);
        traceStream.close();
        LOGGER.info("");
        LOGGER.info("Stats");
        LOGGER.info("=====");

        LOGGER.info("Open Time  " + toMsNiceString(indexStartTime - batchStartTime));
        LOGGER.info("Index Time " + toMsNiceString(indexEndTime - indexStartTime));
        LOGGER.info("Close Time " + toMsNiceString(batchEndTime - indexEndTime));
        LOGGER.info("Total Time " + toMsNiceString(batchEndTime - batchStartTime));
        LOGGER.info("");
        LOGGER.info("Final Docs PS " + ModelStringUtil.formatCsv(docsPerSecond));

        traceStream = LoggerPrintStream.create(LOGGER, false);
        for (int i = 0; i < writers.length; i++) {
            LOGGER.info("");
            final IndexShardWriterImpl impl = writers[i];
            LOGGER.info("Writer " + StringUtils.leftPad(String.valueOf(i), 2));
            LOGGER.info("=========");
            impl.trace(traceStream);
        }
        traceStream.close();

        LOGGER.info("");
        LOGGER.info("Search");
        LOGGER.info("=====");

        try {
            final IndexShardSearcherImpl[] reader = new IndexShardSearcherImpl[indexShards.length];
            final IndexReader[] readers = new IndexReader[indexShards.length];
            for (int i = 0; i < reader.length; i++) {
                reader[i] = new IndexShardSearcherImpl(indexShards[i]);
                reader[i].open();
                readers[i] = reader[i].getReader();
            }

            for (final String arg : docArgs) {
                doSearchOnField(readers, arg);
            }

            doSearchOnField(readers, "multifield");
            doSearchOnField(readers, "dupfield");

            LOGGER.info("=====");

            for (int i = 0; i < reader.length; i++) {
                reader[i].close();
            }

        } catch (final Exception ex) {
            ex.printStackTrace();
        }

    }

    private void doSearchOnField(final IndexReader[] readers, final String arg) throws IOException {
        long timeSearching = 0;
        long searchesDone = 0;
        long matchesFound = 0;

        for (int i = 0; i < 10000; i++) {
            final long startTime = System.currentTimeMillis();
            final Query query = new TermQuery(new Term(arg, "user" + getRandomSkewed()));

            for (final IndexReader reader : readers) {
                final List<Integer> documentIdList = new ArrayList<>();
                final IndexSearcher searcher = new IndexSearcher(reader);
                searcher.search(query, new SimpleCollector() {
                    private int docBase;

                    @Override
                    protected void doSetNextReader(final LeafReaderContext context) throws IOException {
                        super.doSetNextReader(context);
                        docBase = context.docBase;
                    }

                    @Override
                    public void collect(final int doc) throws IOException {
                        documentIdList.add(docBase + doc);
                    }

                    @Override
                    public boolean needsScores() {
                        return false;
                    }
                });

                for (final Integer docId : documentIdList) {
                    final Document doc = reader.document(docId);
                    final String streamId = doc.get(IndexConstants.STREAM_ID);
                    final String eventId = doc.get(IndexConstants.EVENT_ID);
                }
                matchesFound += documentIdList.size();
            }

            timeSearching += System.currentTimeMillis() - startTime;
            searchesDone++;

        }
        LOGGER.info("Performed " + ModelStringUtil.formatCsv(searchesDone) + " searches on arg " + arg + " in "
                + ModelStringUtil.formatDurationString(timeSearching) + " and found "
                + ModelStringUtil.formatCsv(matchesFound) + " matches");
    }

    public String toMsNiceString(final long value) {
        return StringUtils.leftPad(ModelStringUtil.formatCsv(value), 10) + " ms";
    }
}
