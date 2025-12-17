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

package stroom.db.util;

import java.util.Objects;
import javax.sql.DataSource;

public class DbDataSource {

    private final DbUrl dbUrl;
    private final DataSource dataSource;

    public DbDataSource(final DbUrl dbUrl,
                        final DataSource dataSource) {
        this.dbUrl = dbUrl;
        this.dataSource = dataSource;
    }

    public DbUrl getDbUrl() {
        return dbUrl;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DbDataSource that = (DbDataSource) o;
        return Objects.equals(dbUrl, that.dbUrl) &&
                Objects.equals(dataSource, that.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbUrl, dataSource);
    }
}
