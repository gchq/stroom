package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.docref.DocRef;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.inject.Inject;

public class CoprocessorsFactory {

    private final SizesProvider sizesProvider;
    private final DataStoreFactory dataStoreFactory;

    @Inject
    public CoprocessorsFactory(final SizesProvider sizesProvider,
                               final DataStoreFactory dataStoreFactory) {
        this.sizesProvider = sizesProvider;
        this.dataStoreFactory = dataStoreFactory;
    }

    public List<CoprocessorSettings> createSettings(final SearchRequest searchRequest) {
        // Group common settings.
        final Map<TableSettings, Set<String>> groupMap = new HashMap<>();
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                final String componentId = resultRequest.getComponentId();
                final TableSettings tableSettings = resultRequest.getMappings().get(0);
                if (tableSettings != null) {
                    Set<String> set = groupMap.computeIfAbsent(tableSettings, k -> new HashSet<>());
                    set.add(componentId);
                }
            }
        }

        final List<CoprocessorSettings> coprocessorSettings = new ArrayList<>(groupMap.size());
        int i = 0;
        for (final Entry<TableSettings, Set<String>> entry : groupMap.entrySet()) {
            final TableSettings tableSettings = entry.getKey();
            final Set<String> componentIds = entry.getValue();
            final String[] componentIdArray = componentIds.stream().sorted().toArray(String[]::new);
            coprocessorSettings.add(new TableCoprocessorSettings(i++, componentIdArray, tableSettings));
        }

        return coprocessorSettings;
    }

    public Coprocessors create(final SearchRequest searchRequest,
                               final DataStoreSettings dataStoreSettings) {
        final List<CoprocessorSettings> coprocessorSettingsList = createSettings(searchRequest);
        return create(
                searchRequest.getKey(),
                coprocessorSettingsList,
                searchRequest.getQuery().getParams(),
                dataStoreSettings);
    }

    public Coprocessors create(final QueryKey queryKey,
                               final List<CoprocessorSettings> coprocessorSettingsList,
                               final List<Param> params,
                               final DataStoreSettings dataStoreSettings) {
        // Create a field index map.
        final FieldIndex fieldIndex = new FieldIndex();

        // Create a parameter map.
        final Map<String, String> paramMap = ParamUtil.createParamMap(params);

        // Create error consumer.
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

        final Map<Integer, Coprocessor> coprocessorMap = new HashMap<>();
        final Map<String, TableCoprocessor> componentIdCoprocessorMap = new HashMap<>();
        if (coprocessorSettingsList != null) {
            for (final CoprocessorSettings coprocessorSettings : coprocessorSettingsList) {
                final Coprocessor coprocessor = create(
                        queryKey,
                        coprocessorSettings,
                        fieldIndex,
                        paramMap,
                        errorConsumer,
                        dataStoreSettings);

                if (coprocessor != null) {
                    coprocessorMap.put(coprocessorSettings.getCoprocessorId(), coprocessor);

                    if (coprocessor instanceof TableCoprocessor) {
                        final TableCoprocessor tableCoprocessor = (TableCoprocessor) coprocessor;
                        final TableCoprocessorSettings tableCoprocessorSettings =
                                (TableCoprocessorSettings) coprocessorSettings;
                        for (final String componentId : tableCoprocessorSettings.getComponentIds()) {
                            componentIdCoprocessorMap.put(componentId, tableCoprocessor);
                        }
                    }
                }
            }
        }

        // Group coprocessors by extraction pipeline.
        final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap = new HashMap<>();
        coprocessorMap.values().forEach(coprocessor -> {
            DocRef extractionPipeline = null;

            if (coprocessor instanceof TableCoprocessor) {
                final TableCoprocessor tableCoprocessor = (TableCoprocessor) coprocessor;
                if (tableCoprocessor.getTableSettings().extractValues()) {
                    extractionPipeline = tableCoprocessor.getTableSettings().getExtractionPipeline();
                }
            }

            extractionPipelineCoprocessorMap.computeIfAbsent(extractionPipeline, k ->
                    new HashSet<>()).add(coprocessor);
        });

        return new Coprocessors(
                Collections.unmodifiableMap(coprocessorMap),
                Collections.unmodifiableMap(componentIdCoprocessorMap),
                Collections.unmodifiableMap(extractionPipelineCoprocessorMap),
                fieldIndex,
                errorConsumer);
    }

    private Coprocessor create(final QueryKey queryKey,
                               final CoprocessorSettings settings,
                               final FieldIndex fieldIndex,
                               final Map<String, String> paramMap,
                               final ErrorConsumer errorConsumer,
                               final DataStoreSettings dataStoreSettings) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
            final DataStore dataStore = create(
                    queryKey,
                    String.valueOf(tableCoprocessorSettings.getCoprocessorId()),
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings,
                    errorConsumer);
            return new TableCoprocessor(tableSettings, dataStore, errorConsumer);
        } else if (settings instanceof EventCoprocessorSettings) {
            final EventCoprocessorSettings eventCoprocessorSettings = (EventCoprocessorSettings) settings;
            return new EventCoprocessor(eventCoprocessorSettings, fieldIndex, errorConsumer);
        }

        return null;
    }

    private DataStore create(final QueryKey queryKey,
                             final String componentId,
                             final TableSettings tableSettings,
                             final FieldIndex fieldIndex,
                             final Map<String, String> paramMap,
                             final DataStoreSettings dataStoreSettings,
                             final ErrorConsumer errorConsumer) {
        final Sizes storeSizes = sizesProvider.getStoreSizes();

        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table
        // and the default maximum sizes.
        final Sizes defaultMaxResultsSizes = sizesProvider.getDefaultMaxResultsSizes();
        final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);
        final DataStoreSettings modifiedSettings =
                dataStoreSettings.copy()
                        .maxResults(maxResults)
                        .storeSize(storeSizes).build();

        return dataStoreFactory.create(
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                modifiedSettings,
                errorConsumer);
    }
}
