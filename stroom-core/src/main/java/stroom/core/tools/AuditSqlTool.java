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

package stroom.core.tools;

public class AuditSqlTool {

    static void outTable(final String table) {
        System.out.println("alter table " + table + " add `CRT_MS` bigint(20) NOT NULL;");
        System.out.println("alter table " + table + " add `CRT_USER` varchar(255) default NULL;");
        System.out.println("alter table " + table + " add `UPD_MS` bigint(20) NOT NULL;");
        System.out.println("alter table " + table + " add `UPD_USER` varchar(255) default NULL;");
        System.out.println("update " + table + " set CRT_MS = " + System.currentTimeMillis() + ";");
        System.out.println("update " + table + " set UPD_MS = " + System.currentTimeMillis() + ";");
    }

    public static void main(final String[] args) {
        outTable("search_index_shard");
    }
}
