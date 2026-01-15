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

package stroom.dashboard.impl;


import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.query.api.Column;
import stroom.query.api.FlatResult;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Row;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

class TestSearchResponseMapper {

    @Test
    void testResponse() {
        final SearchResponseMapper mapper = new SearchResponseMapper();
        final DashboardSearchResponse result = mapper.mapResponse(null, getSearchResponse());
        System.out.println(result);
    }

    private SearchResponse getSearchResponse() {
        final List<Column> fields = Collections.singletonList(Column
                .builder()
                .id("test")
                .name("test")
                .expression("${test}")
                .build());
        final List<Row> rows = Collections.singletonList(Row.builder()
                .groupKey("groupKey")
                .values(Collections.singletonList("test"))
                .depth(5)
                .build());
        final TableResult tableResult = new TableResult("table-1234",
                fields,
                rows,
                new OffsetRange(1, 2),
                1L,
                Collections.singletonList("tableResultError"),
                Collections.singletonList(new ErrorMessage(Severity.ERROR, "tableResultError")));
        return new SearchResponse(
                new QueryKey(UUID.randomUUID().toString()),
                List.of("highlight1", "highlight2"),
                List.of(tableResult, getVisResult1()),
                Collections.singletonList("some error"),
                false,
                Collections.singletonList(new ErrorMessage(Severity.ERROR, "some error")));
    }

    private FlatResult getVisResult1() {
        final List<Column> structure = new ArrayList<>();
        structure.add(Column.builder().id("val1").name("val1").format(Format.GENERAL).build());
        structure.add(Column.builder().id("val1").name("val2").format(Format.NUMBER).build());
        structure.add(Column.builder().id("val1").name("val3").format(Format.NUMBER).build());
        structure.add(Column.builder().id("val1").name("val4").format(Format.GENERAL).build());

        final List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("test0", 0.4, 234, "this0"));
        data.add(Arrays.asList("test1", 0.5, 25634, "this1"));
        data.add(Arrays.asList("test2", 0.6, 27, "this2"));
        data.add(Arrays.asList("test3", 0.7, 344, "this3"));
        data.add(Arrays.asList("test4", 0.2, 8984, "this4"));
        data.add(Arrays.asList("test5", 0.33, 3244, "this5"));
        data.add(Arrays.asList("test6", 34.66, 44, "this6"));
        data.add(Arrays.asList("test7", 2.33, 74, "this7"));

        return new FlatResult("vis-1234", structure, data, 200L,
                Collections.singletonList("visResultError"),
                Collections.singletonList(new ErrorMessage(Severity.ERROR, "visResultError")));
    }

//    private VisResult getVisResult2() {
//        Field[][] structure = new Field[3][];
//        structure[0] = new Field[]{new Field("key1", Type.GENERAL)};
//        structure[1] = new Field[]{new Field("key2", Type.GENERAL)};
//        structure[2] = new Field[]{new Field("val1", Type.GENERAL),
//        new Field("val2", Type.NUMBER), new Field("val3", Type.NUMBER), new Field("val4", Type.GENERAL)};
//
//        Object[][] data = new Object[8][];
//        data[0] = new Object[]{"test0", 0.4, 234, "this0"};
//        data[1] = new Object[]{"test1", 0.5, 25634, "this1"};
//        data[2] = new Object[]{"test2", 0.6, 27, "this2"};
//        data[3] = new Object[]{"test3", 0.7, 344, "this3"};
//        data[4] = new Object[]{"test4", 0.2, 8984, "this4"};
//        data[5] = new Object[]{"test5", 0.33, 3244, "this5"};
//        data[6] = new Object[]{"test6", 34.66, 44, "this6"};
//        data[7] = new Object[]{"test7", 2.33, 74, "this7"};
//
//        Object[] parentKey1 = new Object[]{"key1"};
//        Object[] parentKey2 = new Object[]{"key2"};
//
//        Node[] innerNodes = new Node[2];
//        innerNodes[0] = new Node(new Object[][]{parentKey1, new Object[]{"innerKey1"}}, data, null, null, null);
//        innerNodes[1] = new Node(new Object[][]{parentKey1, new Object[]{"innerKey2"}}, data, null, null, null);
//
//        Node[] nodes = new Node[2];
//        nodes[0] = new Node(new Object[][]{parentKey1}, innerNodes, null, null, null);
//        nodes[1] = new Node(new Object[][]{parentKey2}, data, null, null, null);
//
//        VisResult visResult = new VisResult("vis-5555", structure, nodes, null, null, null, 200L, "visResultError");
//
//        return visResult;
//    }
}
