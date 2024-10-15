/*
 * Copyright 2024 Crown Copyright
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

package stroom.contentindex;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.docref.StringMatchLocation;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.index.lucene980.Lucene980LockFactory;
import stroom.index.lucene980.analyser.AnalyzerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.lucene980.analysis.Analyzer;
import org.apache.lucene980.analysis.LowerCaseFilter;
import org.apache.lucene980.analysis.TokenStream;
import org.apache.lucene980.analysis.Tokenizer;
import org.apache.lucene980.analysis.core.KeywordAnalyzer;
import org.apache.lucene980.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene980.analysis.ngram.NGramTokenizer;
import org.apache.lucene980.document.Document;
import org.apache.lucene980.document.Field;
import org.apache.lucene980.document.Field.Store;
import org.apache.lucene980.document.FieldType;
import org.apache.lucene980.document.TextField;
import org.apache.lucene980.index.DirectoryReader;
import org.apache.lucene980.index.IndexOptions;
import org.apache.lucene980.index.IndexWriter;
import org.apache.lucene980.index.IndexWriterConfig;
import org.apache.lucene980.index.StoredFields;
import org.apache.lucene980.queryparser.classic.ParseException;
import org.apache.lucene980.queryparser.classic.QueryParser;
import org.apache.lucene980.search.IndexSearcher;
import org.apache.lucene980.search.MatchAllDocsQuery;
import org.apache.lucene980.search.NGramPhraseQuery;
import org.apache.lucene980.search.PhraseQuery;
import org.apache.lucene980.search.Query;
import org.apache.lucene980.search.ScoreDoc;
import org.apache.lucene980.store.Directory;
import org.apache.lucene980.store.NIOFSDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Singleton // Only want ctor re-index to happen once on boot
public class LuceneContentIndex implements ContentIndex, EntityEvent.Handler {

    private static final int MIN_GRAM = 1;
    private static final int MAX_GRAM = 2;
    private static final int MAX_HIGHLIGHTS = 100;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentIndex.class);

    private static final String TYPE = "type";
    private static final String UUID = "uuid";
    private static final String NAME = "name";
    private static final String EXTENSION = "extension";
    //    private static final String DATA = "data";
//    private static final String DATA_CS = "data_cs";
    private static final String DATA_NGRAM = "data_ngram";
    private static final String DATA_CS_NGRAM = "data_cs_ngram";
    private static final String TEXT = "text";

    private final Map<String, ContentIndexable> indexableMap;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final Analyzer analyzer;
    private final ArrayBlockingQueue<EntityEvent> changes = new ArrayBlockingQueue<>(10000);
    private final Path docIndexDir;
    private final Directory directory;

    @Inject
    public LuceneContentIndex(final TempDirProvider tempDirProvider,
                              final Set<ContentIndexable> indexables,
                              final SecurityContext securityContext,
                              final TaskContextFactory taskContextFactory) {
        try {
            final Map<String, ContentIndexable> indexableMap = new HashMap<>();
            for (final ContentIndexable indexable : indexables) {
                indexableMap.put(indexable.getType(), indexable);
            }
            this.indexableMap = indexableMap;
            this.securityContext = securityContext;
            this.taskContextFactory = taskContextFactory;

            final Map<String, Analyzer> analyzerMap = new HashMap<>();
            analyzerMap.put(TYPE, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(UUID, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(NAME, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(EXTENSION, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
//            analyzerMap.put(DATA, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
//            analyzerMap.put(DATA_CS, AnalyzerFactory.create(AnalyzerType.KEYWORD, true));
            analyzerMap.put(DATA_NGRAM, new NGramAnalyzer());
            analyzerMap.put(DATA_CS_NGRAM, new NGramCSAnalyzer());
            analyzerMap.put(TEXT, AnalyzerFactory.create(AnalyzerType.KEYWORD, true));
            analyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

            docIndexDir = tempDirProvider.get().resolve("doc-index");
            Files.createDirectories(docIndexDir);

            final boolean validIndex = isValidIndex(docIndexDir, analyzer);
            if (!validIndex) {
                FileUtil.deleteContents(docIndexDir);
            }

            // Create lucene directory object.
            directory = new NIOFSDirectory(docIndexDir, Lucene980LockFactory.get());

            CompletableFuture
                    .runAsync(() -> {
                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                final EntityEvent event = changes.take();
                                final List<EntityEvent> list = new ArrayList<>();
                                list.add(event);
                                changes.drainTo(list);
                                if (!list.isEmpty()) {
                                    updateIndex(list);
                                }
                            }
                        } catch (final InterruptedException e) {
                            LOGGER.error(e::getMessage, e);
                            Thread.currentThread().interrupt();
                            throw new UncheckedInterruptedException(e);
                        }
                    });

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public void flush() {
        final List<EntityEvent> list = new ArrayList<>();
        final EntityEvent event = changes.poll();
        if (event != null) {
            list.add(event);
        }
        changes.drainTo(list);
        updateIndex(list);
    }

    private boolean isValidIndex(final Path dir, final Analyzer analyzer) {
        try (final NIOFSDirectory directory = new NIOFSDirectory(dir, Lucene980LockFactory.get())) {
            try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
                final QueryParser queryParser = new QueryParser("", analyzer);
                final Query query = queryParser.parse("test");
                indexSearcher.search(query, 1);
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void onChange(final EntityEvent event) {
        NullSafe.consume(event, EntityEvent::getDocRef, DocRef::getType, type -> {
            if (indexableMap.containsKey(type)) {
                try {
                    changes.put(event);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedInterruptedException(e);
                }
            }
        });
    }

    public void reindex() {
        securityContext.asProcessingUser(() -> {
            final List<DocRef> docRefs = indexableMap.values()
                    .stream()
                    .flatMap(contentIndexable ->
                            contentIndexable.listDocuments().stream())
                    .toList();

            LOGGER.logDurationIfInfoEnabled(
                    () -> {
                        docRefs.stream()
                                .takeWhile(docRef ->
                                        !Thread.currentThread().isInterrupted())
                                .map(docRef ->
                                        new EntityEvent(docRef, EntityAction.UPDATE))
                                .forEach(this::onChange);
                    },
                    LogUtil.message("Re-index of {} documents in {}",
                            docRefs.size(),
                            docIndexDir.toAbsolutePath().normalize()));
        });
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        return LOGGER.logDurationIfInfoEnabled(() ->
                findInContentUsingIndex(request), "findInContentUsingIndex");
    }

    private ResultPage<DocContentMatch> findInContentUsingIndex(final FindInContentRequest request) {
        try {
            final PageRequest pageRequest = request.getPageRequest();
            final List<DocContentMatch> matches = new ArrayList<>();

            final Query query = getQuery(request.getFilter());
            final ContentHighlighter highlighter = getHighlighter(request.getFilter());

            long total = 0;
            try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                final IndexSearcher searcher = new IndexSearcher(directoryReader);
                final ScoreDoc[] hits = searcher.search(query, 1000000).scoreDocs;
                final StoredFields storedFields = searcher.storedFields();
                for (final ScoreDoc hit : hits) {
                    final int docId = hit.doc;
                    final Document doc = storedFields.document(docId);
                    final DocRef docRef = new DocRef(doc.get(TYPE), doc.get(UUID), doc.get(NAME));
                    final String extension = doc.get(EXTENSION);
                    final String text = doc.get(TEXT);
                    final List<StringMatchLocation> highlights = highlighter.getHighlights(text, 1);
                    if (!highlights.isEmpty()) {
                        if (securityContext.hasDocumentPermission(docRef, DocumentPermissionNames.READ)) {
                            if (total >= pageRequest.getOffset() &&
                                    total < pageRequest.getOffset() + pageRequest.getLength()) {
                                try {
                                    matches.add(DocContentMatch.create(docRef,
                                            extension,
                                            text,
                                            highlights.getFirst()));
                                } catch (final Exception e) {
                                    LOGGER.debug(e::getMessage, e);
                                }
                            }
                            total++;
                        }
                    }
                }
            }

            return new ResultPage<>(matches, PageResponse
                    .builder()
                    .offset(pageRequest.getOffset())
                    .length(matches.size())
                    .total(total)
                    .exact(total == pageRequest.getOffset() + matches.size())
                    .build());
        } catch (final ParseException | RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            return ResultPage.empty();
        }
    }

    private ContentHighlighter getHighlighter(final StringMatch stringMatch) throws ParseException {
        return new BasicContentHighlighter(stringMatch);
//
//        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
//            return new BasicContentHighlighter(stringMatch);
//        }
//
//        final Query basicQuery = getBasicQuery(stringMatch);
//        return new LuceneContentHighlighter(getNGramField(stringMatch), basicQuery);
    }

    private String getNGramField(final StringMatch stringMatch) {
        return stringMatch.isCaseSensitive()
                ? DATA_CS_NGRAM
                : DATA_NGRAM;
    }

//    private String[] getFields(final StringMatch stringMatch) {
//        return stringMatch.isCaseSensitive()
//                ? new String[]{DATA_CS_NGRAM, DATA_CS}
//                : new String[]{DATA_NGRAM, DATA};
//    }

    private String getQueryText(final StringMatch stringMatch) {
        String value = stringMatch.isCaseSensitive()
                ? stringMatch.getPattern()
                : stringMatch.getPattern().toLowerCase(Locale.ROOT);
        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            if (!value.endsWith("/") || value.length() == 1) {
                value = value + "/";
            }
        }
        return value;
    }

    private Query getQuery(final StringMatch stringMatch) throws ParseException {
        // If we are going to do a regex then we'll just scan all content and do regex.
        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
            return new MatchAllDocsQuery();
        }

        final String field = getNGramField(stringMatch);
        final String queryText = getQueryText(stringMatch);

        final QueryParser queryParser = new QueryParser(field, analyzer) {
            @Override
            protected Query newFieldQuery(Analyzer analyzer, String field, String queryText, boolean quoted)
                    throws ParseException {
                // Ensure data is always processed as a phrase.
                if (isNGram(field) && !quoted && queryText.length() > 1) {
                    final Query q = super.newFieldQuery(analyzer, field, queryText, true);
                    if (q instanceof final PhraseQuery phraseQuery) {
                        return new NGramPhraseQuery(MAX_GRAM, phraseQuery);
                    }
                    return q;
                }
                return super.newFieldQuery(analyzer, field, queryText, quoted);
            }
        };

        queryParser.setAllowLeadingWildcard(true);
        return queryParser.parse(queryText);
    }

    private Query getBasicQuery(final StringMatch stringMatch) throws ParseException {
        final String field = getNGramField(stringMatch);
        final String queryText = getQueryText(stringMatch);
        final QueryParser queryParser = new QueryParser(field, analyzer) {
            @Override
            protected Query newFieldQuery(Analyzer analyzer, String field, String queryText, boolean quoted)
                    throws ParseException {
                // Ensure data is always processed as a phrase.
                if (isNGram(field) && !quoted && queryText.length() > 1) {
                    return super.newFieldQuery(analyzer, field, queryText, true);
                }
                return super.newFieldQuery(analyzer, field, queryText, quoted);
            }
        };

        queryParser.setAllowLeadingWildcard(true);
        return queryParser.parse(queryText);
    }

    private boolean isNGram(final String field) {
        return DATA_NGRAM.equals(field) || DATA_CS_NGRAM.equals(field);
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        if (!securityContext.hasDocumentPermission(request.getDocRef(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserIdentityForAudit(),
                    "You do not have read permission on " + request.getDocRef());
        }

        try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
            final Query docQuery = createDocQuery(request.getDocRef(), request.getExtension());
            final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
            final ScoreDoc[] hits = indexSearcher.search(docQuery, 1).scoreDocs;
            if (hits.length > 0) {
                final int docId = hits[0].doc;
                final StoredFields storedFields = indexSearcher.storedFields();
                final Document doc = storedFields.document(docId);
                final String text = doc.get(TEXT);

                try {
                    final ContentHighlighter highlighter = getHighlighter(request.getFilter());
                    final List<StringMatchLocation> highlights = highlighter.getHighlights(text, MAX_HIGHLIGHTS);
                    return new DocContentHighlights(request.getDocRef(), text, highlights);

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }
        } catch (final ParseException e) {
            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }

        return null;
    }

    private synchronized void updateIndex(final List<EntityEvent> list) {
        securityContext.asProcessingUser(() -> taskContextFactory.context("Indexing Content Changes",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
                    try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
                        final int totalCount = list.size();
                        final AtomicInteger count = new AtomicInteger();
                        list.stream()
                                .takeWhile(createTaskTerminatedCheck(taskContext))
                                .forEach(event -> {
                                    count.incrementAndGet();

                                    try {
                                        switch (event.getAction()) {
                                            case CREATE -> {
                                                setTaskContextInfo(
                                                        taskContext, event, "Adding", count, totalCount);
                                                addDoc(taskContext, writer, event.getDocRef());
                                            }
                                            case UPDATE -> {
                                                setTaskContextInfo(
                                                        taskContext, event, "Updating", count, totalCount);
                                                deleteDoc(writer, event.getDocRef());
                                                addDoc(taskContext, writer, event.getDocRef());
                                            }
                                            case DELETE -> {
                                                setTaskContextInfo(
                                                        taskContext, event, "Deleting", count, totalCount);
                                                deleteDoc(writer, event.getDocRef());
                                            }
                                        }
                                    } catch (final Exception e) {
                                        LOGGER.error(e::getMessage, e);
                                    }
                                });

                        taskContext.info(() -> "Committing");
                        LOGGER.logDurationIfDebugEnabled(
                                ThrowingRunnable.unchecked(writer::commit),
                                () -> LogUtil.message("Commit {} event(s)", count));

                        taskContext.info(() -> "Flushing");
                        LOGGER.logDurationIfDebugEnabled(
                                ThrowingRunnable.unchecked(writer::flush),
                                () -> LogUtil.message("Flush {} event(s)", count));
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }).run());
    }

    private static Predicate<Object> createTaskTerminatedCheck(final TaskContext taskContext) {
        return obj -> {
            if (taskContext.isTerminated()) {
                LOGGER.info("Task is terminated: '{}'", taskContext);
                return false;
            } else if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("Task thread is interrupted: '{}'", taskContext);
                return false;
            } else {
                return true;
            }
        };
    }

    private static void setTaskContextInfo(final TaskContext taskContext,
                                           final EntityEvent event,
                                           final String action,
                                           final AtomicInteger count,
                                           final int totalCount) {
        taskContext.info(
                () -> LogUtil.message("{}: {} ({} of {})",
                        action,
                        DocRefUtil.createSimpleDocRefString(event.getDocRef()),
                        count,
                        totalCount),
                LOGGER);
    }

    private void addDoc(final TaskContext taskContext, final IndexWriter writer, final DocRef docRef) {
        final ContentIndexable contentIndexable = indexableMap.get(docRef.getType());
        if (contentIndexable != null) {
            final Map<String, String> dataMap = contentIndexable.getIndexableData(docRef);
            if (NullSafe.hasEntries(dataMap)) {
                dataMap.entrySet()
                        .stream()
                        .takeWhile(createTaskTerminatedCheck(taskContext))
                        .forEach(entry -> {
                            final String extension = entry.getKey();
                            final String data = entry.getValue();
                            try {
                                index(writer, docRef, extension, data);
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });
            }
        }
    }

    private void deleteDoc(final IndexWriter writer, final DocRef docRef) {
        try {
            final Query query = createDocQuery(docRef, null);
            writer.deleteDocuments(query);
        } catch (final ParseException | IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private Query createDocQuery(final DocRef docRef, final String extension) throws ParseException {
        final StringBuilder sb = new StringBuilder();
        sb.append("+");
        sb.append(TYPE);
        sb.append(":\"");
        sb.append(docRef.getType());
        sb.append("\" ");
        sb.append("+");
        sb.append(UUID);
        sb.append(":\"");
        sb.append(docRef.getUuid());
        sb.append("\"");

        if (extension != null) {
            sb.append(" ");
            sb.append("+");
            sb.append(EXTENSION);
            sb.append(":\"");
            sb.append(extension);
            sb.append("\"");
        }

        final QueryParser queryParser = new QueryParser("", analyzer);
        return queryParser.parse(sb.toString());
    }

    private void index(final IndexWriter writer,
                       final DocRef docRef,
                       final String extension,
                       final String data) throws IOException {
        final Document document = new Document();
        document.add(new TextField(TYPE, docRef.getType(), Store.YES));
        document.add(new TextField(UUID, docRef.getUuid(), Store.YES));
        document.add(new TextField(NAME, docRef.getName(), Store.YES));
        document.add(new TextField(EXTENSION, extension, Store.YES));
        document.add(createField(DATA_NGRAM, data));
        document.add(createField(DATA_CS_NGRAM, data));
//        document.add(createField(DATA, data));
//        document.add(createField(DATA_CS, data));
        document.add(new TextField(TEXT, data, Store.YES));
        writer.addDocument(document);
    }

    private Field createField(final String name, final String data) {
        final FieldType fieldType = new FieldType();
        // Indexes documents, frequencies, positions and offsets:
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        // Field's value is NOT stored in the index:
        fieldType.setStored(false);
        // Store index data in term vectors:
        fieldType.setStoreTermVectors(true);
        // Store token character offsets in term vectors:
        fieldType.setStoreTermVectorOffsets(true);
        // Store token positions in term vectors:
        fieldType.setStoreTermVectorPositions(true);
        // Do NOT store token payloads into the term vector:
        fieldType.setStoreTermVectorPayloads(true);
        return new Field(name, data, fieldType);
    }


    // --------------------------------------------------------------------------------


    static class NGramAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            TokenStream tokenStream = new LowerCaseFilter(tokenizer);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenStream);
        }
    }


    // --------------------------------------------------------------------------------


    static class NGramCSAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenizer);
        }
    }
}
