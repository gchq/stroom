package stroom.index.lucene;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardSearchConfig;
import stroom.index.impl.IndexShardWriterCache;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.impl.SearchConfig;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.lucene.search.IndexSearcher;

class LuceneShardSearcherFactory {

    private final IndexShardWriterCache indexShardWriterCache;
    private final Provider<IndexShardSearchConfig> shardSearchConfigProvider;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final PathCreator pathCreator;
    private final WordListProvider dictionaryStore;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    LuceneShardSearcherFactory(final IndexShardWriterCache indexShardWriterCache,
                               final Provider<IndexShardSearchConfig> shardSearchConfigProvider,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final PathCreator pathCreator,
                               final WordListProvider dictionaryStore,
                               final Provider<SearchConfig> searchConfigProvider) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.shardSearchConfigProvider = shardSearchConfigProvider;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.pathCreator = pathCreator;
        this.dictionaryStore = dictionaryStore;
        this.searchConfigProvider = searchConfigProvider;
    }

    public stroom.index.impl.LuceneShardSearcher create(final DocRef indexDocRef,
                                                        final IndexFieldCache indexFieldCache,
                                                        final ExpressionOperator expression,
                                                        final DateTimeSettings dateTimeSettings,
                                                        final QueryKey queryKey) {
        IndexSearcher.setMaxClauseCount(searchConfigProvider.get().getMaxBooleanClauseCount());
        return new LuceneShardSearcher(
                indexShardWriterCache,
                shardSearchConfigProvider.get(),
                executorProvider,
                taskContextFactory,
                pathCreator,
                indexDocRef,
                indexFieldCache,
                expression,
                dictionaryStore,
                dateTimeSettings,
                queryKey);
    }
}
