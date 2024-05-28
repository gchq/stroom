package stroom.index.lucene980;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.docref.DocRef;
import stroom.docref.StringMatch.MatchType;
import stroom.docref.StringMatchLocation;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.index.lucene980.analyser.AnalyzerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringMatcher;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.lucene980.analysis.Analyzer;
import org.apache.lucene980.analysis.core.KeywordAnalyzer;
import org.apache.lucene980.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene980.document.Document;
import org.apache.lucene980.document.Field.Store;
import org.apache.lucene980.document.TextField;
import org.apache.lucene980.index.DirectoryReader;
import org.apache.lucene980.index.IndexWriter;
import org.apache.lucene980.index.IndexWriterConfig;
import org.apache.lucene980.index.StoredFields;
import org.apache.lucene980.index.Term;
import org.apache.lucene980.queryparser.classic.ParseException;
import org.apache.lucene980.queryparser.classic.QueryParser;
import org.apache.lucene980.search.IndexSearcher;
import org.apache.lucene980.search.Query;
import org.apache.lucene980.search.RegexpQuery;
import org.apache.lucene980.search.ScoreDoc;
import org.apache.lucene980.store.Directory;
import org.apache.lucene980.store.NIOFSDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Singleton
@EntityEventHandler(
        action = {EntityAction.CREATE, EntityAction.UPDATE, EntityAction.DELETE})
public class ContentIndexImpl implements ContentIndex, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentIndexImpl.class);

    private final TempDirProvider tempDirProvider;
    private final Map<String, ContentIndexable> indexableMap;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final NoIndex noIndex;
    private volatile ContentIndex currentIndex;

    @Inject
    public ContentIndexImpl(final TempDirProvider tempDirProvider,
                            final Set<ContentIndexable> indexables,
                            final SecurityContext securityContext,
                            final TaskContextFactory taskContextFactory) {
        this.tempDirProvider = tempDirProvider;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        indexableMap = new HashMap<>();
        for (final ContentIndexable indexable : indexables) {
            indexableMap.put(indexable.getType(), indexable);
        }

        noIndex = new NoIndex(indexableMap);
        currentIndex = noIndex;
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        return LOGGER.logDurationIfInfoEnabled(() ->
                currentIndex.findInContent(request), "findInContent");
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        return LOGGER.logDurationIfInfoEnabled(() ->
                currentIndex.fetchHighlights(request), "fetchHighlights");
    }

    public synchronized void reindex() {
        try {
            if (currentIndex instanceof final Index index) {
//                    index.reindex();
            } else {
                currentIndex = new Index(tempDirProvider, indexableMap, securityContext, taskContextFactory);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            currentIndex = noIndex;
        }
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null && event.getDocRef() != null && event.getDocRef().getType() != null) {
            if (indexableMap.containsKey(event.getDocRef().getType())) {
                final ContentIndex contentIndex = currentIndex;
                if (contentIndex instanceof final EntityEvent.Handler handler) {
                    handler.onChange(event);
                }
            }
        }
    }

    private static class Index implements ContentIndex, EntityEvent.Handler {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Index.class);

        private static final String TYPE = "type";
        private static final String UUID = "uuid";
        private static final String NAME = "name";
        private static final String EXTENSION = "extension";
        private static final String DATA = "data";
        private static final String DATA_CS = "dataCS";
//        private static final String[] ALL_FIELDS = new String[]{TYPE, UUID, NAME, EXTENSION, DATA};
//        private static final String[] ALL_CS_FIELDS = new String[]{TYPE_CS, UUID_CS, NAME_CS, EXTENSION_CS, DATA_CS};


        private final Path docIndexDir;
        private final Map<String, ContentIndexable> indexableMap;
        private final SecurityContext securityContext;
        private final TaskContextFactory taskContextFactory;
        private final Analyzer analyzer;
        private final ArrayBlockingQueue<EntityEvent> changes = new ArrayBlockingQueue<>(1000);
        private final AtomicBoolean resolvingChanges = new AtomicBoolean();
        private volatile Directory directory;

        public Index(final TempDirProvider tempDirProvider,
                     final Map<String, ContentIndexable> indexableMap,
                     final SecurityContext securityContext,
                     final TaskContextFactory taskContextFactory) throws IOException {
            this.securityContext = securityContext;
            this.taskContextFactory = taskContextFactory;
            this.indexableMap = indexableMap;

            docIndexDir = tempDirProvider.get().resolve("doc-index");
            Files.createDirectories(docIndexDir);

            final Path indexDir = docIndexDir.resolve(DateUtil.createFileDateTimeString(Instant.now()));
            Files.createDirectories(indexDir);

            // Create lucene directory object.
            directory = new NIOFSDirectory(indexDir, Lucene980LockFactory.get());

            final Map<String, Analyzer> analyzerMap = new HashMap<>();
            analyzerMap.put(TYPE, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(UUID, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(NAME, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(EXTENSION, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));
            analyzerMap.put(DATA, AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, false));
            analyzerMap.put(DATA_CS, AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, true));
            analyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

            indexAll(directory);
        }

        public void reindex() {
            try {
                final Instant oldest = Instant.now().minus(10, ChronoUnit.MINUTES);

                final Path indexDir = docIndexDir.resolve(DateUtil.createFileDateTimeString(Instant.now()));
                Files.createDirectories(indexDir);

                // Create lucene directory object.
                final NIOFSDirectory directory = new NIOFSDirectory(indexDir, Lucene980LockFactory.get());
                indexAll(directory);

                final Directory currentDirectory = this.directory;
                this.directory = directory;
                currentDirectory.close();

                // Delete old indexes
                deleteOldIndexes(oldest);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
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

                final Query query = getQuery(request);
                final StringMatcher stringMatcher = new StringMatcher(request.getFilter());
                long total = 0;
                try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                    final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
                    final ScoreDoc[] hits = indexSearcher.search(query, 1000000).scoreDocs;
                    final StoredFields storedFields = indexSearcher.storedFields();
                    for (int i = 0; i < hits.length; i++) {
                        final Document hitDoc = storedFields.document(hits[i].doc);
                        final DocRef docRef = new DocRef(hitDoc.get(TYPE), hitDoc.get(UUID), hitDoc.get(NAME));
                        if (securityContext.hasDocumentPermission(docRef, DocumentPermissionNames.READ)) {
                            if (total >= pageRequest.getOffset() &&
                                    total < pageRequest.getOffset() + pageRequest.getLength()) {
                                try {
                                    final String extension = hitDoc.get(EXTENSION);
                                    final String data = hitDoc.get(DATA);
                                    final Optional<StringMatchLocation> optional = stringMatcher.match(data);
                                    optional.ifPresent(match -> {
                                        String sample = data.substring(
                                                Math.max(0, match.getOffset()),
                                                Math.min(data.length() - 1, match.getOffset() + match.getLength()));
                                        if (sample.length() > 100) {
                                            sample = sample.substring(0, 100);
                                        }

                                        final DocContentMatch docContentMatch = DocContentMatch
                                                .builder()
                                                .docRef(docRef)
                                                .extension(extension)
                                                .location(match)
                                                .sample(sample)
                                                .build();
                                        matches.add(docContentMatch);
                                    });
                                } catch (final RuntimeException e) {
                                    LOGGER.debug(e::getMessage, e);
                                }
                            }
                            total++;
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

        private Query getQuery(final FindInContentRequest request) throws ParseException {
            final String field = request.getFilter().isCaseSensitive()
                    ? DATA_CS
                    : DATA;
            final String value = request.getFilter().isCaseSensitive()
                    ? request.getFilter().getPattern()
                    : request.getFilter().getPattern().toLowerCase(Locale.ROOT);
            final Query query;
            if (MatchType.REGEX.equals(request.getFilter().getMatchType())) {
                query = new RegexpQuery(new Term(field, value));
            } else {
                final QueryParser queryParser = new QueryParser(field, analyzer);
                query = queryParser.parse(value);
            }
            return query;
        }

        @Override
        public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
            if (!securityContext.hasDocumentPermission(request.getDocRef(), DocumentPermissionNames.READ)) {
                throw new PermissionException(securityContext.getUserIdentityForAudit(),
                        "You do not have read permission on " + request.getDocRef());
            }

            try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                final Query query = createDocQuery(request.getDocRef(), request.getExtension());
                final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
                final ScoreDoc[] hits = indexSearcher.search(query, 1).scoreDocs;
                if (hits.length > 0) {
                    final StringMatcher stringMatcher = new StringMatcher(request.getFilter());
                    final StoredFields storedFields = indexSearcher.storedFields();
                    final Document hitDoc = storedFields.document(hits[0].doc);
                    final String data = hitDoc.get(DATA);

                    try {
                        final List<StringMatchLocation> matchList = stringMatcher.match(data, 100);
                        if (!matchList.isEmpty()) {
                            return new DocContentHighlights(request.getDocRef(), data, matchList);
                        }
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

        private void indexAll(final Directory directory) {
            // We want to index everything regardless of user.
            securityContext.asProcessingUser(() -> taskContextFactory.context("Indexing Content",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> {
                        try {
                            final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
                            try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
                                for (final ContentIndexable contentIndexable : indexableMap.values()) {
                                    contentIndexable.listDocuments().forEach(docRef -> {
                                        taskContext.info(() -> "Indexing: " +
                                                DocRefUtil.createSimpleDocRefString(docRef));
                                        contentIndexable.getIndexableData(docRef).forEach((extension, data) -> {
                                            try {
                                                index(writer, docRef, extension, data);
                                            } catch (final IOException e) {
                                                LOGGER.error(e::getMessage, e);
                                            }
                                        });
                                    });
                                }
                                writer.commit();
                                writer.flush();
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e::getMessage, e);
                            throw new UncheckedIOException(e);
                        }
                    }).run());
        }

        @Override
        public void onChange(final EntityEvent event) {
            try {
                changes.put(event);
                resolveChanges();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UncheckedInterruptedException(e);
            }
        }

        private void resolveChanges() {
            if (resolvingChanges.compareAndSet(false, true)) {
                CompletableFuture
                        .runAsync(() -> {
                            final List<EntityEvent> list = new ArrayList<>();
                            changes.drainTo(list);
                            if (!list.isEmpty()) {
                                updateIndex(list);
                            }
                        })
                        .whenComplete((r, t) -> {
                            resolvingChanges.set(false);
                            if (!changes.isEmpty()) {
                                resolveChanges();
                            }
                        });
            }
        }

        private void updateIndex(final List<EntityEvent> list) {
            securityContext.asProcessingUser(() -> taskContextFactory.context("Indexing Content Changes",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> {
                        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
                        try (final IndexWriter writer = new IndexWriter(directory,
                                indexWriterConfig)) {
                            list.forEach(event -> {
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
                            });

                            writer.commit();
                            writer.flush();
                        } catch (final IOException e) {
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
                        } catch (final IOException e) {
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
            document.add(new TextField(DATA, data, Store.YES));
            document.add(new TextField(DATA_CS, data, Store.NO));
            writer.addDocument(document);
        }

        private void deleteOldIndexes(final Instant oldest) {
            if (Files.isDirectory(docIndexDir)) {
                try (final Stream<Path> stream = Files.list(docIndexDir)) {
                    stream.forEach(path -> {
                        try {
                            if (Files.isDirectory(path)) {
                                String name = path.getFileName().toString();
                                name = name.replaceAll("#", ":");
                                name = name.replaceAll(",", ".");
                                final Instant instant = DateUtil.parseNormalDateTimeStringToInstant(name);
                                if (instant.isBefore(oldest)) {
                                    FileUtil.deleteDir(path);
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
    }

    private static class NoIndex implements ContentIndex {

        private final Map<String, ContentIndexable> indexableMap;

        public NoIndex(final Map<String, ContentIndexable> indexableMap) {
            this.indexableMap = indexableMap;
        }

        @Override
        public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
            final PageRequest pageRequest = request.getPageRequest();
            final List<DocContentMatch> matches = new ArrayList<>();
            final AtomicLong total = new AtomicLong();

            final StringMatcher stringMatcher = new StringMatcher(request.getFilter());
            indexableMap.values().forEach(indexable -> indexable.listDocuments().forEach(docRef ->
                    indexable.getIndexableData(docRef).forEach((extension, data) -> {
                        final Optional<StringMatchLocation> optional = stringMatcher.match(data);
                        optional.ifPresent(match -> {
                            if (total.get() >= pageRequest.getOffset() &&
                                    total.get() < pageRequest.getOffset() + pageRequest.getLength()) {
                                String sample = data.substring(
                                        Math.max(0, match.getOffset()),
                                        Math.min(data.length() - 1,
                                                match.getOffset() + match.getLength()));
                                if (sample.length() > 100) {
                                    sample = sample.substring(0, 100);
                                }

                                final DocContentMatch docContentMatch = DocContentMatch
                                        .builder()
                                        .docRef(docRef)
                                        .extension(extension)
                                        .location(match)
                                        .sample(sample)
                                        .build();
                                matches.add(docContentMatch);
                            }
                            total.incrementAndGet();
                        });
                    })));

            return new ResultPage<>(matches, PageResponse
                    .builder()
                    .offset(pageRequest.getOffset())
                    .length(matches.size())
                    .total(total.get())
                    .exact(total.get() == pageRequest.getOffset() + matches.size())
                    .build());
        }

        @Override
        public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
            final StringMatcher stringMatcher = new StringMatcher(request.getFilter());

            final ContentIndexable indexable = indexableMap.get(request.getDocRef().getType());
            if (indexable != null) {
                final Map<String, String> map = indexable.getIndexableData(request.getDocRef());
                if (map != null) {
                    final String data = map.get(request.getExtension());
                    if (data != null) {
                        final List<StringMatchLocation> matchList = stringMatcher.match(data, 100);
                        if (!matchList.isEmpty()) {
                            return new DocContentHighlights(request.getDocRef(), data, matchList);
                        }
                    }
                }
            }

            return null;
        }
    }
}
