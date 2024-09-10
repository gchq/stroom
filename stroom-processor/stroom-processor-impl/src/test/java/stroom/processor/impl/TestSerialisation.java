package stroom.processor.impl;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ProcessorRow;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSerialisation {

    @Test
    void test() throws Exception {
        final ObjectMapper objectMapper = JsonUtil.getMapper();

        final List<ProcessorListRow> rows = new ArrayList<>();
        rows.add(new ProcessorRow(new Expander(), new Processor()));
        rows.add(new ProcessorFilterRow(new ProcessorFilter()));

        final ProcessorListRowResultPage resultPage1 = new ProcessorListRowResultPage(
                rows,
                ResultPage.createPageResponse(rows));
        final String result1 = objectMapper.writeValueAsString(resultPage1);
        System.out.println(result1);
        final ProcessorListRowResultPage resultPage2 = objectMapper.readValue(
                result1,
                ProcessorListRowResultPage.class);
        final String result2 = objectMapper.writeValueAsString(resultPage2);
        System.out.println(result2);

        assertThat(resultPage2.getValues().get(0)).isInstanceOf(ProcessorRow.class);
        assertThat(resultPage2.getValues().get(1)).isInstanceOf(ProcessorFilterRow.class);
    }
}
