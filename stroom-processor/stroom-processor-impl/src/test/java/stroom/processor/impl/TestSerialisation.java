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
