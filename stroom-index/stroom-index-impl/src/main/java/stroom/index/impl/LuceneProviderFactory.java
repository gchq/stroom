package stroom.index.impl;

import stroom.index.shared.LuceneVersion;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LuceneProviderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneProviderFactory.class);

    public final Map<LuceneVersion, LuceneProvider> luceneProviders;

    @Inject
    LuceneProviderFactory(final Set<LuceneProvider> providers) {
        luceneProviders = providers
                .stream()
                .collect(Collectors.toMap(LuceneProvider::getLuceneVersion, Function.identity()));
    }

    public LuceneProvider get(final LuceneVersion luceneVersion) {
        final LuceneProvider luceneProvider = luceneProviders.get(luceneVersion);
        if (luceneProvider == null) {
            LOGGER.error("No Lucene provider found for version " + luceneVersion);
            throw new RuntimeException("No Lucene provider found for version " + luceneVersion);
        }
        return luceneProvider;
    }
}
