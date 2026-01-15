/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
