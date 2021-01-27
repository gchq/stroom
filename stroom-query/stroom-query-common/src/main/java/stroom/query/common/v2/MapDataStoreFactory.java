package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.OutputFactory;
import stroom.query.api.v2.TableSettings;

import javax.inject.Inject;
import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {
    private final OutputFactory outputFactory;

    @Inject
    public MapDataStoreFactory(final OutputFactory outputFactory) {
        this.outputFactory = outputFactory;
    }

    public DataStore create(final String queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {
        return new MapDataStore(
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize,
                outputFactory);
    }
}
