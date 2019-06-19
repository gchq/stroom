package stroom.streamstore.server;

import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSourceField;
import stroom.dictionary.server.DictionaryStore;
import stroom.explorer.server.ExplorerService;

import javax.inject.Inject;
import java.util.Map;

@Component
public class ExpressionMatcherFactory {
    private final DictionaryStore dictionaryStore;
    private final ExplorerService explorerService;

    @Inject
    public ExpressionMatcherFactory(final DictionaryStore dictionaryStore, final ExplorerService explorerService) {
        this.dictionaryStore = dictionaryStore;
        this.explorerService = explorerService;
    }

    public ExpressionMatcher create(final Map<String, DataSourceField> fieldMap) {
        return new ExpressionMatcher(fieldMap, dictionaryStore, explorerService);
    }
}
