package stroom.query.common.v2;

import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.Map;

public interface DataStoreFactory {

    DataStore create(
            ExpressionContext expressionContext,
            SearchRequestSource searchRequestSource,
            QueryKey queryKey,
            String componentId,
            TableSettings tableSettings,
            FieldIndex fieldIndex,
            Map<String, String> paramMap,
            DataStoreSettings dataStoreSettings,
            ErrorConsumer errorConsumer);

    StoreSizeSummary getTotalSizeOnDisk();

    class StoreSizeSummary {

        private final long totalSizeOnDisk;
        private final int storeCount;

        public StoreSizeSummary(final long totalSizeOnDisk,
                                final int storeCount) {
            this.totalSizeOnDisk = totalSizeOnDisk;
            this.storeCount = storeCount;
        }

        public long getTotalSizeOnDisk() {
            return totalSizeOnDisk;
        }

        public int getStoreCount() {
            return storeCount;
        }

        @Override
        public String toString() {
            return "StoreSizeSummary{" +
                    "totalSizeOnDisk=" + totalSizeOnDisk +
                    ", storeCount=" + storeCount +
                    '}';
        }
    }
}
