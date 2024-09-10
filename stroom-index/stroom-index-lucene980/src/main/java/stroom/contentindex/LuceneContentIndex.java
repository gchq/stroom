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
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
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

            final Path docIndexDir = tempDirProvider.get().resolve("doc-index");
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
        try {
            if (event != null && event.getDocRef() != null && event.getDocRef().getType() != null) {
                if (indexableMap.containsKey(event.getDocRef().getType())) {
                    changes.put(event);
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void reindex() {
        securityContext.asProcessingUser(() -> indexableMap.forEach((type, indexable) -> {
            final Set<DocRef> docRefs = indexable.listDocuments();
            docRefs.forEach(docRef -> onChange(new EntityEvent(docRef, EntityAction.UPDATE)));
        }));
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
            final Highlighter highlighter = getHighlighter(request.getFilter());

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
                    if (highlighter.filter(text)) {
                        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                            if (total >= pageRequest.getOffset() &&
                                    total < pageRequest.getOffset() + pageRequest.getLength()) {
                                try {
                                    final List<StringMatchLocation> highlights = highlighter
                                            .getHighlights(directoryReader, docId, text, 1);
                                    if (!highlights.isEmpty()) {
                                        matches.add(DocContentMatch.create(docRef,
                                                extension,
                                                text,
                                                highlights.getFirst()));
                                    } else {
                                        matches.add(DocContentMatch.create(docRef,
                                                extension,
                                                text,
                                                new StringMatchLocation(0, 0)));
                                    }
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

    private Highlighter getHighlighter(final StringMatch stringMatch) throws ParseException {
        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
            return new BasicContentHighlighter(stringMatch);
        }

        final Query basicQuery = getBasicQuery(stringMatch);
        return new LuceneContentHighlighter(getNGramField(stringMatch), basicQuery);
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
        if (!securityContext.hasDocumentPermission(request.getDocRef(), DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
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
                    final Highlighter highlighter = getHighlighter(request.getFilter());
                    final List<StringMatchLocation> highlights = highlighter
                            .getHighlights(directoryReader, docId, text, MAX_HIGHLIGHTS);
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
                    try (final IndexWriter writer = new IndexWriter(directory,
                            indexWriterConfig)) {
                        list.forEach(event -> {
                            try {
                                switch (event.getAction()) {
                                    case CREATE -> {
                                        taskContext.info(() -> "Adding: " +
                                                DocRefUtil.createSimpleDocRefString(event.getDocRef()));
                                        addDoc(writer, event.getDocRef());
                                    }
                                    case UPDATE -> {
                                        taskContext.info(() -> "Updating: " +
                                                DocRefUtil.createSimpleDocRefString(event.getDocRef()));
                                        deleteDoc(writer, event.getDocRef());
                                        addDoc(writer, event.getDocRef());
                                    }
                                    case DELETE -> {
                                        taskContext.info(() -> "Deleting: " +
                                                DocRefUtil.createSimpleDocRefString(event.getDocRef()));
                                        deleteDoc(writer, event.getDocRef());
                                    }
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });

                        taskContext.info(() -> "Committing");
                        writer.commit();
                        taskContext.info(() -> "Flushing");
                        writer.flush();
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }).run());
    }

    private void addDoc(final IndexWriter writer, final DocRef docRef) {
        final ContentIndexable contentIndexable = indexableMap.get(docRef.getType());
        if (contentIndexable != null) {
            final Map<String, String> dataMap = contentIndexable.getIndexableData(docRef);
            if (dataMap != null && !dataMap.isEmpty()) {
                dataMap.forEach((extension, data) -> {
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

    static class NGramAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            TokenStream tokenStream = new LowerCaseFilter(tokenizer);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenStream);
        }
    }

    static class NGramCSAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenizer);
        }
    }
}
