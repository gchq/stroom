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
