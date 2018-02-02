/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import stroom.statistics.shared.StatisticType;
import stroom.util.DatabaseTool;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that can be run as a main method to manually perform aggregation
 */
public class SQLStatisticValueBatchSaveServiceTestDatabaseTool extends DatabaseTool {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(SQLStatisticValueBatchSaveServiceTestDatabaseTool.class);

    public static void main(final String[] args) throws Exception {
        new SQLStatisticValueBatchSaveServiceTestDatabaseTool().doMain(args);
    }

    @Override
    public void run() {
        try (final Connection connection = getConnection()) {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final SQLStatisticValueBatchSaveService statisticValueBatchSaveService = new SQLStatisticValueBatchSaveService(null) {
                @Override
                protected Connection getConnection() {
                    return connection;
                }

                @Override
                void releaseConnection(final Connection connection) {
                    // Do nothing.
                }
            };

            for (int l = 0; l < 100; l++) {
                final List<SQLStatisticValueSourceDO> batch = new ArrayList<SQLStatisticValueSourceDO>();
                for (int i = 0; i < 1000; i++) {
                    final SQLStatisticValueSourceDO statisticValueSource = new SQLStatisticValueSourceDO();
                    statisticValueSource.setCreateMs(System.currentTimeMillis());
                    statisticValueSource.setName("BATCHTEST" + i);
                    statisticValueSource.setType(StatisticType.VALUE);
                    statisticValueSource.setValue(System.currentTimeMillis());
                    batch.add(statisticValueSource);
                }

                statisticValueBatchSaveService.saveBatchStatisticValueSource_PreparedStatement(batch);
                statisticValueBatchSaveService.saveBatchStatisticValueSource_String(batch);
            }
            LOGGER.info("run() - took %s", logExecutionTime);

        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }
}
