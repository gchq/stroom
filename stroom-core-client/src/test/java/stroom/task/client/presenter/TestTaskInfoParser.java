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

package stroom.task.client.presenter;

import stroom.task.client.presenter.TaskInfoParser.TaskInfoKey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskInfoParser {

    @Test
    void test() {
        final String taskInfo = "filter id=4, filter uuid=dcda9c31-51d7-42a2-8f9b-7b50566a170a, " +
                          "pipeline uuid=ca55998a-8ec2-487b-a228-85666b839ae6, pipeline name=CountPipelineSQL";
        final TaskInfoParser parser = new TaskInfoParser(taskInfo);

        assertThat(parser.getString(TaskInfoKey.FILTER_ID)).isEqualTo("4");
        assertThat(parser.getLong(TaskInfoKey.FILTER_ID)).isEqualTo(4);
        assertThat(parser.getString(TaskInfoKey.PIPELINE_UUID)).isEqualTo("ca55998a-8ec2-487b-a228-85666b839ae6");
    }

    @Test
    void testWithPrecedingText() {
        final String taskInfo = "Loading reference data stream_id=123456, part=7890";

        final TaskInfoParser parser = new TaskInfoParser(taskInfo);

        assertThat(parser.getString(TaskInfoKey.STREAM_ID)).isEqualTo("123456");
    }

    @Test
    void testReturnNulls() {
        final String taskInfo = "filter id= , filter uuid=,pipeline uuid= , pipeline name=";
        final TaskInfoParser parser = new TaskInfoParser(taskInfo);

        assertThat(parser.getString(TaskInfoKey.FILTER_ID)).isNull();
        assertThat(parser.getString(TaskInfoKey.PIPELINE_UUID)).isNull();
    }

}
