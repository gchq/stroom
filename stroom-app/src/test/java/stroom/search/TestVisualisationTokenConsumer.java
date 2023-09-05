package stroom.search;

import stroom.dictionary.impl.DictionaryStore;
import stroom.index.impl.IndexStore;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.SearchRequest;
import stroom.query.language.SearchRequestBuilder;
import stroom.search.impl.EventSearchTaskHandler;
import stroom.task.api.TaskContextFactory;
import stroom.task.impl.ExecutorProviderImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.ContentImportService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Provider;

public class TestVisualisationTokenConsumer extends AbstractCoreIntegrationTest {
    private static boolean doneSetup;
    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private IndexStore indexStore;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private Executor executor;
    @Inject
    private TaskContextFactory taskContextFactory;
    @Inject
    private Provider<EventSearchTaskHandler> eventSearchTaskHandlerProvider;
    @Inject
    private ExecutorProviderImpl executorProvider;
    @Inject
    private SearchRequestBuilder searchRequestBuilder;
    @Inject
    private ContentImportService contentImportService;


    @BeforeEach
    void setup() {
        if (!doneSetup) {
            commonIndexingTestHelper.setup();
            contentImportService.importVisualisations();
            doneSetup = true;
        }
    }

    @Test
    void testVis() {
        String queryString = """
                from "Test index"
                eval EventTime = roundDay(EventTime)
                eval count = count()
                group by EventTime
                select EventTime, count
                vis as LineChart (x = EventTime, y = count)
                """;


        SearchRequest searchRequest = new SearchRequest(
                null,
                null,
                null,
                null,
                DateTimeSettings.builder().build(),
                false);
        searchRequest = searchRequestBuilder.create(queryString, searchRequest);
//        searchRequest = dataSourceResolver.resolveDataSource(searchRequest);

//        test(queryString, 5);


    }

}
