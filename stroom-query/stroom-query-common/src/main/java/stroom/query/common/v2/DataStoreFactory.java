package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.TableSettings;

import java.util.Map;

public interface DataStoreFactory {

    DataStore create(String queryKey,
                     String componentId,
                     TableSettings tableSettings,
                     FieldIndex fieldIndex,
                     Map<String, String> paramMap,
                     Sizes maxResults,
                     Sizes storeSize,
                     boolean producePayloads,
                     ErrorConsumer errorConsumer);
}
