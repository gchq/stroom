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

package stroom.node.shared;

import stroom.entity.shared.OrderBy;
import stroom.util.shared.SharedObject;

/**
 * API to table status
 */
public class DBTableStatus implements SharedObject {
    private static final long serialVersionUID = -5061144975403180924L;

    public static final OrderBy DATABASE = new OrderBy("Database");
    public static final OrderBy TABLE = new OrderBy("Table");
    public static final OrderBy ROW_COUNT = new OrderBy("Count");
    public static final OrderBy DATA_SIZE = new OrderBy("Data Size");
    public static final OrderBy INDEX_SIZE = new OrderBy("Index Size");

    public static final String MANAGE_DB_PERMISSION = "Manage DB";

    private String db;
    private String table;
    private Long count;
    private Long dataSize;
    private Long indexSize;

    public String getDb() {
        return db;
    }

    public void setDb(final String db) {
        this.db = db;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public Long getDataSize() {
        return dataSize;
    }

    public void setDataSize(Long dataSize) {
        this.dataSize = dataSize;
    }

    public Long getIndexSize() {
        return indexSize;
    }

    public void setIndexSize(Long indexSize) {
        this.indexSize = indexSize;
    }

}
