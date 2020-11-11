package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

public class CoprocessorsFactory {
    private final SizesProvider sizesProvider;

    @Inject
    CoprocessorsFactory(final SizesProvider sizesProvider) {
        this.sizesProvider = sizesProvider;
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
            final CoprocessorKey key = new CoprocessorKey(i++, componentIds.stream().sorted().toArray(String[]::new));
            coprocessorSettings.add(new TableCoprocessorSettings(key, tableSettings));
        }

        return coprocessorSettings;
    }

    public Coprocessors create(final SearchRequest searchRequest) {
        final List<CoprocessorSettings> coprocessorSettingsList = createSettings(searchRequest);
        return create(coprocessorSettingsList, searchRequest.getQuery().getParams());
    }

    public Coprocessors create(final List<CoprocessorSettings> coprocessorSettingsList,
                               final List<Param> params) {
        // Create a field index map.
        final FieldIndex fieldIndex = new FieldIndex();

        // Create a parameter map.
        final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(params);

        // Create error consumer.
        final ErrorConsumer errorConsumer = new ErrorConsumer();

        final Map<CoprocessorKey, Coprocessor> coprocessorMap = new HashMap<>();
        final Map<String, TableCoprocessor> componentIdCoprocessorMap = new HashMap<>();
        if (coprocessorSettingsList != null) {
            for (final CoprocessorSettings coprocessorSettings : coprocessorSettingsList) {
                final Coprocessor coprocessor = create(coprocessorSettings, fieldIndex, paramMap, errorConsumer);

                if (coprocessor != null) {
                    coprocessorMap.put(coprocessorSettings.getCoprocessorKey(), coprocessor);

                    if (coprocessor instanceof TableCoprocessor) {
                        final TableCoprocessor tableCoprocessor = (TableCoprocessor) coprocessor;
                        for (final String componentId : coprocessorSettings.getCoprocessorKey().getComponentIds()) {
                            componentIdCoprocessorMap.put(componentId, tableCoprocessor);
                        }
                    }
                }
            }
        }

        // Group coprocessors by extraction pipeline.
        final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap = new HashMap<>();
        coprocessorMap.values().forEach(coprocessor -> {
            if (coprocessor instanceof TableCoprocessor) {
                final TableCoprocessor tableCoprocessor = (TableCoprocessor) coprocessor;
                DocRef extractionPipeline = null;
                if (tableCoprocessor.getTableSettings().extractValues()) {
                    extractionPipeline = tableCoprocessor.getTableSettings().getExtractionPipeline();
                }
                extractionPipelineCoprocessorMap.computeIfAbsent(extractionPipeline, k -> new HashSet<>()).add(coprocessor);
            }
        });

        return new Coprocessors(
                Collections.unmodifiableMap(coprocessorMap),
                Collections.unmodifiableMap(componentIdCoprocessorMap),
                Collections.unmodifiableMap(extractionPipelineCoprocessorMap),
                fieldIndex,
                errorConsumer);
    }

    private Coprocessor create(final CoprocessorSettings settings,
                               final FieldIndex fieldIndex,
                               final Map<String, String> paramMap,
                               final Consumer<Throwable> errorConsumer) {
        final CoprocessorKey coprocessorKey = settings.getCoprocessorKey();
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
            final TableDataStore tableDataStore = create(coprocessorKey, tableSettings, fieldIndex, paramMap);
            return new TableCoprocessor(coprocessorKey, tableSettings, tableDataStore, errorConsumer);
        } else if (settings instanceof EventCoprocessorSettings) {
            final EventCoprocessorSettings eventCoprocessorSettings = (EventCoprocessorSettings) settings;
            return new EventCoprocessor(coprocessorKey, eventCoprocessorSettings, fieldIndex, errorConsumer);
        }

        return null;
    }

    private TableDataStore create(final CoprocessorKey coprocessorKey,
                                  final TableSettings tableSettings,
                                  final FieldIndex fieldIndex,
                                  final Map<String, String> paramMap) {
        final Sizes storeSizes = sizesProvider.getStoreSizes();

        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table and the default maximum sizes.
        final Sizes defaultMaxResultsSizes = sizesProvider.getDefaultMaxResultsSizes();
        final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);

        return new TableDataStore(coprocessorKey, tableSettings, fieldIndex, paramMap, maxResults, storeSizes);
    }
}
