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

package stroom.processor.impl.db.migration;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V07_06_00_300__processor_filter_pre_migration_checks extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_06_00_300__processor_filter_pre_migration_checks.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final boolean error = false;


        // NOT SURE IF WE NEED PRE MIG CHECKS BUT WE COULD DETECT PFs WITHOUT OWNER OR WITH MULTIPLE OWNERS????

////        'UPDATE processor_filter pf, doc_permission dp ',
////                'SET pf.run_as_user_uuid = dp.user_uuid ',
////                'WHERE pf.uuid = dp.doc_uuid ',
////                'AND dp.permission = "Owner"');
//
//        // Detect any activities that we can't find users for.
//        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
//                """
//                        SELECT DISTINCT(pf.uuid)
//                        FROM processor_filter pf
//                        LEFT OUTER JOIN stroom_user su
//                        ON (su.name = a.user_id)
//                        WHERE su.name IS NULL;""")) {
//            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
//                while (resultSet.next()) {
//                    try {
//                        final String user = resultSet.getString(1);
//                        LOGGER.error(() ->
//                                "Pre migration check failure:\n`activity.user_id` '" +
//                                        user +
//                                        "' not found in `stroom_user`");
//                        error = true;
//                    } catch (final RuntimeException e) {
//                        LOGGER.error(e.getMessage(), e);
//                    }
//                }
//            }
//        }

        if (error) {
            throw new RuntimeException("Pre migration check failure");
        }
    }
}
