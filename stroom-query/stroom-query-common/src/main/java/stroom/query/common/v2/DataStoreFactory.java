package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;

import java.util.Map;

public interface DataStoreFactory {

    DataStore create(QueryKey queryKey,
                     String componentId,
                     TableSettings tableSettings,
                     FieldIndex fieldIndex,
                     Map<String, String> paramMap,
                     Sizes maxResults,
                     Sizes storeSize,
                     boolean producePayloads,
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
