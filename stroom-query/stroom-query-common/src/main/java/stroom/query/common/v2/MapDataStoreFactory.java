package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.TableSettings;

import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {
    public DataStore create(final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {
        return new MapDataStore(
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }
}
