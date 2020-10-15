package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.Param;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

public class CoprocessorsFactory {
    private final CoprocessorFactory coprocessorFactory;

    @Inject
    CoprocessorsFactory(final CoprocessorFactory coprocessorFactory) {
        this.coprocessorFactory = coprocessorFactory;
    }

    public Coprocessors create(final Map<CoprocessorKey, CoprocessorSettings> coprocessorSettingsMap,
                               final String[] storedFields,
                               final List<Param> params,
                               final Consumer<Error> errorConsumer) {
        // Create a parameter map.
        final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(params);

        final Set<NewCoprocessor> coprocessors = new HashSet<>();
        final Map<String, FieldIndexMap> fieldIndexes = new HashMap<>();
        if (coprocessorSettingsMap != null) {
            // Get an array of stored index fields that will be used for getting stored data.
            final FieldIndexMap storedFieldIndexMap = new FieldIndexMap();
            for (final String storedField : storedFields) {
                storedFieldIndexMap.create(storedField, true);
            }

            for (final Entry<CoprocessorKey, CoprocessorSettings> entry : coprocessorSettingsMap.entrySet()) {
                final CoprocessorKey coprocessorKey = entry.getKey();
                final CoprocessorSettings coprocessorSettings = entry.getValue();

                // Figure out where the fields required by this coprocessor will be found.
                FieldIndexMap fieldIndexMap = storedFieldIndexMap;
                if (coprocessorSettings.extractValues() && coprocessorSettings.getExtractionPipeline() != null
                        && coprocessorSettings.getExtractionPipeline().getUuid() != null) {
                    fieldIndexMap = fieldIndexes.computeIfAbsent(coprocessorSettings.getExtractionPipeline().getUuid(), k -> new FieldIndexMap(true));
                }

                final Coprocessor coprocessor = coprocessorFactory.create(coprocessorSettings, fieldIndexMap, paramMap);

                if (coprocessor != null) {
                    final NewCoprocessor newCoprocessor = new NewCoprocessor(
                            coprocessorKey,
                            coprocessorSettings,
                            errorConsumer,
                            coprocessor,
                            fieldIndexMap);
                    coprocessors.add(newCoprocessor);
                }
            }
        }

        return new Coprocessors(coprocessors);
    }
}
