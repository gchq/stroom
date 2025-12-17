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

package stroom.statistics.impl.sql;

import stroom.util.DatabaseTool;
import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that can be run as a main method to manually perform aggregation
 */
public class SQLStatisticValueBatchSaveServiceTestDatabaseTool extends DatabaseTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SQLStatisticValueBatchSaveServiceTestDatabaseTool.class);

    public static void main(final String[] args) {
        new SQLStatisticValueBatchSaveServiceTestDatabaseTool().doMain(args);
    }

    @Override
    public void run() {
        try {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final SQLStatisticValueBatchSaveService statisticValueBatchSaveService =
                    new SQLStatisticValueBatchSaveService(null) {
                        @Override
                        protected Connection getConnection() {
                            return SQLStatisticValueBatchSaveServiceTestDatabaseTool.this.getConnection();
                        }
                    };

            for (int l = 0; l < 100; l++) {
                final List<SQLStatValSourceDO> batch = new ArrayList<>();
                for (int i = 0; i < 1000; i++) {
                    final long value = System.currentTimeMillis();
                    final long count = 100;

                    final SQLStatValSourceDO statisticValueSource = SQLStatValSourceDO.createValueStat(
                            System.currentTimeMillis(),
                            "BATCHTEST" + i,
                            value,
                            count);

                    batch.add(statisticValueSource);
                }
                statisticValueBatchSaveService.saveBatchStatisticValueSource_SinglePreparedStatement(batch);
            }
            LOGGER.info("run() - took {}", logExecutionTime);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            System.exit(1);
        }
    }
}
