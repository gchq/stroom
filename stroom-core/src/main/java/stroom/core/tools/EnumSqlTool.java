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

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.job.shared.JobNode.JobType;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.HasPrimitiveValue;

public class EnumSqlTool {

    public static void process(final String name, final HasPrimitiveValue[] list) {
        System.out.println("insert into enum_mig");
        System.out.println("\t( name, str, id )");
        System.out.println("values");
        for (int i = 0; i < list.length; i++) {
            System.out.print("\t('" + name + "','" + list[i].toString() + "'," + list[i].getPrimitiveValue() + ")");
            if (i + 1 < list.length) {
                System.out.println(",");
            } else {
                System.out.println();
            }
        }
        System.out.println(";");

    }

    public static void main(final String[] args) {
        System.out.println("drop table if exists enum_mig;");
        System.out.println("create table enum_mig (");
        System.out.println("\tname varchar(255) not null,");
        System.out.println("\tstr varchar(255) not null,");
        System.out.println("\tid tinyint,");
        System.out.println("\tprimary key (name, str, id),");
        System.out.println("\tkey (name,id)");
        System.out.println(");");
        System.out.println();

        process("TaskStatus", TaskStatus.values());
        process("JobType", JobType.values());
        process("FsVolumeUseStatus", FsVolume.VolumeUseStatus.values());
//        process("StreamStatus", StreamStatus.values());
    }
}
