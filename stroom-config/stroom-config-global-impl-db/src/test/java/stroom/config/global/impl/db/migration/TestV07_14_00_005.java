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

package stroom.config.global.impl.db.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests `V07_14_00_005__document_asset_property_rename.sql`, which re-points
/// `stroom.visualisationAsset[Db].*` property overrides to `stroom.documentAsset[Db].*`.
public class TestV07_14_00_005 extends AbstractConfigMigrationTest {

    @Override
    protected String getTestDataScript() {
        return scriptBuilder()
                // A plain rename.
                .append(insertConfig("stroom.visualisationAsset.mimetypes", "old-mimetypes"))
                // The DB block shares the prefix, so it is renamed too.
                .append(insertConfig("stroom.visualisationAssetDb.connection.jdbcDriverUrl", "old-url"))
                // Already set under the new name - the newer value must win and the old row go.
                .append(insertConfig("stroom.visualisationAsset.assetDir", "old-dir"))
                .append(insertConfig("stroom.documentAsset.assetDir", "new-dir"))
                // The YAML root key is not how config.name is stored, so these must be untouched.
                .append(insertConfig("appConfig.visualisationAsset.maxUploadSize", "unrelated"))
                .append(insertConfig("stroom.data.meta.metaTypes", "Raw Events"))
                .build();
    }

    @Test
    void test() {
        final List<Row> rows = getRows("""
                        select name, val
                        from config
                        """,
                rec -> new Row(
                        rec.get("name", String.class),
                        rec.get("val", String.class)));

        assertThat(rows)
                .containsExactlyInAnyOrder(
                        new Row("stroom.documentAsset.mimetypes", "old-mimetypes"),
                        new Row("stroom.documentAssetDb.connection.jdbcDriverUrl", "old-url"),
                        new Row("stroom.documentAsset.assetDir", "new-dir"),
                        new Row("appConfig.visualisationAsset.maxUploadSize", "unrelated"),
                        new Row("stroom.data.meta.metaTypes", "Raw Events"));
    }

    private static String insertConfig(final String name, final String val) {
        return """
                INSERT INTO config (
                    version,
                    create_time_ms,
                    create_user,
                    update_time_ms,
                    update_user,
                    name,
                    val)
                VALUES (
                    1,
                    0,
                    'admin',
                    0,
                    'admin',
                    %s,
                    %s);
                """.formatted(quote(name), quote(val));
    }


    // --------------------------------------------------------------------------------


    private record Row(String name, String val) {

    }
}
