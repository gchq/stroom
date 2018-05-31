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

package stroom.util;

import stroom.jobsystem.shared.ClusterLock;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.node.shared.GlobalProperty;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeState;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;
import stroom.util.date.DateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TableSqlTool {
    private static final List<String> TABLE_CREATE_ORDER = Collections.unmodifiableList(Arrays.asList(
            Rack.TABLE_NAME,
            Node.TABLE_NAME,
            StreamType.TABLE_NAME,
            ClusterLock.TABLE_NAME,
            FeedEntity.TABLE_NAME,
            Volume.TABLE_NAME,
            VolumeState.TABLE_NAME,
            GlobalProperty.TABLE_NAME,
            Job.TABLE_NAME,
            JobNode.TABLE_NAME,
            StreamProcessor.TABLE_NAME,
            StreamProcessorFilter.TABLE_NAME,
            StreamType.TABLE_NAME,
            StreamEntity.TABLE_NAME,
            StreamAttributeKey.TABLE_NAME,
            StreamAttributeValue.TABLE_NAME,
            StreamVolume.TABLE_NAME,
            StreamTask.TABLE_NAME));

    public static void main(final String[] args) {
        System.out.println("-- Script created by TableSqlTool on " + DateUtil.createNormalDateTimeString());
        System.out.println();

        final TableSqlToolStyle style = TableSqlToolStyle.valueOf(args[0]);

        if (style == TableSqlToolStyle.RENAME_OLD) {
            for (String table : TABLE_CREATE_ORDER) {
                table = table.toLowerCase();
                System.out.println("alter table " + table + " rename to " + table + "_old;");
            }
        }
        if (style == TableSqlToolStyle.DROP_OLD) {
            final List<String> dropOrder = new ArrayList<>(TABLE_CREATE_ORDER);
            Collections.reverse(dropOrder);
            for (String table : dropOrder) {
                table = table.toLowerCase();
                System.out.println("drop table " + table + "_old;");
            }
        }
        if (style == TableSqlToolStyle.DROP) {
            final List<String> dropOrder = new ArrayList<>(TABLE_CREATE_ORDER);
            Collections.reverse(dropOrder);
            for (String table : dropOrder) {
                table = table.toLowerCase();
                System.out.println("drop table if exists " + table + ";");
            }
        }

        if (style == TableSqlToolStyle.REPAIR) {
            final List<String> dropOrder = new ArrayList<>(TABLE_CREATE_ORDER);
            Collections.reverse(dropOrder);
            for (String table : dropOrder) {
                table = table.toLowerCase();
                System.out.println("repair table " + table + ";");
            }
        }
        if (style == TableSqlToolStyle.INSERT) {
            final List<String> insertOrder = new ArrayList<>(TABLE_CREATE_ORDER);
            for (String table : insertOrder) {
                table = table.toLowerCase();
                System.out.println("insert into table " + table + " ");
            }
        }
        if (style == TableSqlToolStyle.DELETE) {
            final List<String> deleteOrder = new ArrayList<>(TABLE_CREATE_ORDER);
            Collections.reverse(deleteOrder);
            for (String table : deleteOrder) {
                table = table.toLowerCase();
                System.out.println("delete from " + table + ";");
            }
        }

    }

    public enum TableSqlToolStyle {
        RENAME_OLD, DROP_OLD, DROP, REPAIR, INSERT, DELETE
    }
}
