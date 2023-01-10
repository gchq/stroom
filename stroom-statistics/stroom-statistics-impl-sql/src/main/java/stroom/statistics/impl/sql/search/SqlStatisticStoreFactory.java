package stroom.statistics.impl.sql.search;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.inject.Inject;

@SuppressWarnings("unused")
public class SqlStatisticStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticStoreFactory.class);

    private final StatisticStoreCache statisticStoreCache;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;
    private final CoprocessorsFactory coprocessorsFactory;
    private final Statistics statistics;

    @Inject
    public SqlStatisticStoreFactory(final StatisticStoreCache statisticStoreCache,
                                    final StatisticsSearchService statisticsSearchService,
                                    final TaskContextFactory taskContextFactory,
                                    final Executor executor,
                                    final SearchConfig searchConfig,
                                    final UiConfig clientConfig,
                                    final CoprocessorsFactory coprocessorsFactory,
                                    final Statistics statistics) {
        this.statisticStoreCache = statisticStoreCache;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContextFactory = taskContextFactory;
        this.executor = executor;
        this.coprocessorsFactory = coprocessorsFactory;
        this.statistics = statistics;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        final StatisticStoreDoc entity = statisticStoreCache.getStatisticsDataSource(docRef);
        if (entity == null) {
            return null;
        }

        final List<AbstractField> fields = buildFields(entity);

        return DataSource
                .builder()
                .fields(fields)
                .build();
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return new DateField(StatisticStoreDoc.FIELD_NAME_DATE_TIME);
    }

    /**
     * Turn the {@link StatisticStoreDoc} into an {@link List<AbstractField>} object
     * <p>
     * This builds the standard set of fields for a statistics store, which can
     * be filtered by the relevant statistics store instance
     */
    private List<AbstractField> buildFields(final StatisticStoreDoc entity) {
        List<AbstractField> fields = new ArrayList<>();

        // TODO currently only BETWEEN is supported, but need to add support for
        // more conditions like >, >=, <, <=, =
        fields.add(new DateField(StatisticStoreDoc.FIELD_NAME_DATE_TIME,
                true,
                Collections.singletonList(Condition.BETWEEN)));

        // one field per tag
        if (entity.getConfig() != null) {
            final List<Condition> supportedConditions = Arrays.asList(Condition.EQUALS, Condition.IN);

            for (final StatisticField statisticField : entity.getStatisticFields()) {
                // TODO currently only EQUALS is supported, but need to add
                // support for more conditions like CONTAINS
                fields.add(new TextField(statisticField.getFieldName(), true, supportedConditions));
            }
        }

        fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_COUNT, false, Collections.emptyList()));

        if (entity.getStatisticType().equals(StatisticType.VALUE)) {
            fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_VALUE, false, Collections.emptyList()));
        }

        fields.add(new LongField(StatisticStoreDoc.FIELD_NAME_PRECISION_MS, false, Collections.emptyList()));

        // Filter fields.
        if (entity.getConfig() != null) {
            fields = statistics.getSupportedFields(fields);
        }

        return fields;
    }


    @Override
    public Store create(final SearchRequest searchRequest) {
        LOGGER.debug("create called for searchRequest {} ", searchRequest);

        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                                Preconditions.checkNotNull(searchRequest)
                                        .getQuery())
                        .getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(),
                "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(),
                "searchRequest must have at least one resultRequest");

        final StatisticStoreDoc statisticStoreDoc = statisticStoreCache.getStatisticsDataSource(docRef);

        Preconditions.checkNotNull(statisticStoreDoc, "Statistic configuration could not be found for uuid "
                + docRef.getUuid());

        return buildStore(searchRequest, statisticStoreDoc);
    }

    private Store buildStore(final SearchRequest searchRequest,
                             final StatisticStoreDoc statisticStoreDoc) {
        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreDoc);

        //wrap the resultHandler in a new store, initiating the search in the process
        return new SqlStatisticsStore(
                searchRequest,
                statisticStoreDoc,
                statisticsSearchService,
                executor,
                taskContextFactory,
                coprocessorsFactory);
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.DOCUMENT_TYPE;
    }
}
