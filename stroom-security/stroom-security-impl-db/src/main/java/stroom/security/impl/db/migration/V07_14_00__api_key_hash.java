/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.impl.db.migration;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.Base58;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.Objects;

@SuppressWarnings("unused")
public class V07_14_00__api_key_hash extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_14_00__api_key_hash.class);

    // Find all the sha hashes that are hex encoded
    // There is a risk that a base64 encoded hash could be mistaken for a hex-encoded hash,
    // but the chance of a base58 string containing only hex chars is really very slim.
    // 'c' for case-sensitive comparison
    @SuppressWarnings("SqlResolve")
    private static final String SELECT_API_KEYS = """
            SELECT id, api_key_hash
            FROM api_key
            WHERE hash_algorithm IN (?, ?, ?)
            AND REGEXP_LIKE(api_key_hash, '^[0-9a-f]+$', 'c') = true;
            """;

    @SuppressWarnings("SqlResolve")
    private static final String UPDATE_API_KEY_HASH = """
            UPDATE api_key
            SET api_key_hash = ?
            WHERE id = ?
            """;

    @Override
    public void migrate(final Context context) throws Exception {
        final Connection connection = context.getConnection();

        try (final PreparedStatement selectStatement = connection.prepareStatement(SELECT_API_KEYS);
                final PreparedStatement updateStatement = connection.prepareStatement(UPDATE_API_KEY_HASH)) {

            // Only these hashes need mig
            selectStatement.setInt(1, 0); // SHA3_256
            selectStatement.setInt(2, 1); // SHA2_256
            selectStatement.setInt(3, 4); // SHA2_512

            int migrationCount = 0;
            try (final ResultSet resultSet = selectStatement.executeQuery()) {
                while (resultSet.next()) {
                    final int id = resultSet.getInt(1);
                    final String hexHash = resultSet.getString(2);
                    // Convert the hex hash to base58
                    final String base58Hash = hexToBase58(hexHash);

                    LOGGER.info("Migrating API key hash with id {} from hex '{}' to base58 '{}'",
                            id, hexHash, base58Hash);

                    final String hexHash2 = base58ToHex(base58Hash);

                    // Check the decoding/encoding is reversible
                    if (!Objects.equals(hexHash, hexHash2)) {
                        throw new RuntimeException("Error verifying hash encoding for API key with ID " + id);
                    }

                    updateStatement.setString(1, base58Hash);
                    updateStatement.setInt(2, id);
                    updateStatement.addBatch();
                    migrationCount++;
                }
            }
            if (migrationCount > 0) {
                updateStatement.executeBatch();
            }
            LOGGER.info("Migrated {} API key hashes", migrationCount);
        }
    }

    /// Convert base58 to hex
    static String base58ToHex(final String base58Hash) {
        final byte[] hashBytes2 = Base58.decode(base58Hash);
        return HexFormat.of().formatHex(hashBytes2);
    }

    /// Convert hex to base58
    static @NonNull String hexToBase58(final String hexHash) {
        final byte[] hashBytes = HexFormat.of().parseHex(hexHash);
        return Base58.encode(hashBytes);
    }
}
