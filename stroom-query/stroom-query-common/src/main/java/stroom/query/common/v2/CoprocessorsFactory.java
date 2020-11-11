package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.Param;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CoprocessorsFactory {
    private final SizesProvider sizesProvider;

    @Inject
    CoprocessorsFactory(final SizesProvider sizesProvider) {
        this.sizesProvider = sizesProvider;
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

        return new Coprocessors(
                Collections.unmodifiableMap(coprocessorMap),
                Collections.unmodifiableMap(componentIdCoprocessorMap),
                fieldIndex,
                errorConsumer);
    }

    private Coprocessor create(final CoprocessorSettings settings,
                               final FieldIndex fieldIndex,
                               final Map<String, String> paramMap,
                               final Consumer<Throwable> errorConsumer) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableDataStore tableDataStore = create(tableCoprocessorSettings, fieldIndex, paramMap);
            return new TableCoprocessor(tableDataStore, errorConsumer);
        } else if (settings instanceof EventCoprocessorSettings) {
            final EventCoprocessorSettings eventCoprocessorSettings = (EventCoprocessorSettings) settings;
            return new EventCoprocessor(eventCoprocessorSettings, fieldIndex, errorConsumer);
        }

        return null;
    }

    private TableDataStore create(final TableCoprocessorSettings tableCoprocessorSettings,
                                  final FieldIndex fieldIndex,
                                  final Map<String, String> paramMap) {

        final Sizes storeSizes = sizesProvider.getStoreSizes();

        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table and the default maximum sizes.
        final Sizes defaultMaxResultsSizes = sizesProvider.getDefaultMaxResultsSizes();
        final Sizes maxResults = Sizes.min(Sizes.create(tableCoprocessorSettings.getTableSettings().getMaxResults()), defaultMaxResultsSizes);

        return new TableDataStore(tableCoprocessorSettings, fieldIndex, paramMap, maxResults, storeSizes);
    }
}
