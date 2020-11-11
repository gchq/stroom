package stroom.search.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.StaticValueFunction;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.docref.DocRef;
import stroom.index.shared.AnalyzerType;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.query.api.v2.DateTimeFormat;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.CoprocessorKey;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.NodeResult;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.query.common.v2.TablePayload;
import stroom.query.common.v2.TablePayloadSerialiser;
import stroom.task.shared.TaskId;

import com.caucho.hessian.io.Hessian2Output;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

class TestHessian {
    @Test
    void testClusterSearchTask() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addOperator(
                        new ExpressionOperator.Builder()
                                .addTerm("test", Condition.BETWEEN, "test")
                                .build())
                .addTerm("test", Condition.EQUALS, "test")
                .build();

        final QueryKey key = new QueryKey(UUID.randomUUID().toString());
        final Query query = new Query.Builder()
                .dataSource("test", "test", "test")
                .addParam("test", "test")
                .expression(expression)
                .build();

        final List<IndexField> indexFields = createIndexFields();
        final String[] fields = indexFields.stream().map(IndexField::getFieldName).toArray(String[]::new);

        final Field field = new Field(
                "test",
                "test",
                "test",
                new Sort(1, SortDirection.DESCENDING),
                new Filter("in", "out"),
                new Format(new DateTimeFormat("format",
                        new TimeZone(Use.LOCAL, "local", 0, 0))),
                3);
        final TableSettings tableSettings = new TableSettings(
                "test",
                Collections.singletonList(field),
                true,
                new DocRef("test", "test", "test"),
                Arrays.asList(1, 2, 3),
                true);
        final CoprocessorKey coprocessorKey = new CoprocessorKey(100, new String[]{"c1, c2"});
        final CoprocessorSettings coprocessorSettings = new TableCoprocessorSettings(coprocessorKey, tableSettings);

        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                new TaskId("test", null),
                "test",
                key,
                query,
                Arrays.asList(1L, 2L, 3L),
                fields,
                Collections.singletonList(coprocessorSettings),
                "locale",
                1000);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);

        out.writeObject(clusterSearchTask);
        out.close();
    }

    @Test
    void testNodeResult() throws IOException {
        final GroupKey key = new GroupKey(0, null, new Val[]{ValString.create("test")});
        final List<Item> itemList = new ArrayList<>();
        itemList.add(new Item(key, new Generator[]{getGenerator("v1"), getGenerator("v2")}, 0));
        itemList.add(new Item(key, new Generator[]{getGenerator("v4"), getGenerator("v6")}, 0));
        itemList.add(new Item(key, new Generator[]{getGenerator("v7"), getGenerator("v8")}, 0));

        final List<Field> fields = List.of(new Field.Builder().name("f1").build(), new Field.Builder().name("f2").build());
        final CompiledFields compiledFields = new CompiledFields(fields, new FieldIndex(), Collections.emptyMap());
        final TablePayloadSerialiser tablePayloadSerialiser = new TablePayloadSerialiser(compiledFields);
        final Item[] itemsArray = itemList.toArray(new Item[0]);
        final byte[] data = tablePayloadSerialiser.fromQueue(itemsArray);

        final CoprocessorKey coprocessorKey = new CoprocessorKey(100, new String[]{"c1, c2"});
        final TablePayload tablePayload = new TablePayload(coprocessorKey, data);
        final List<Payload> payloads = new ArrayList<>();
        payloads.add(tablePayload);

        final NodeResult nodeResult = new NodeResult(payloads, Collections.singletonList("test"), true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);

        out.writeObject(nodeResult);
        out.close();
    }

    private Generator getGenerator(final String string) {
        return new StaticValueFunction(ValString.create(string)).createGenerator();
    }

    private List<IndexField> createIndexFields() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("Feed"));
        indexFields.add(IndexField.createField("Feed (Keyword)", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("Action"));
        indexFields.add(IndexField.createDateField("EventTime"));
        indexFields.add(IndexField.createField("UserId", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("System"));
        indexFields.add(IndexField.createField("Environment"));
        indexFields.add(IndexField.createField("IPAddress", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("HostName", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("Generator"));
        indexFields.add(IndexField.createField("Command"));
        indexFields.add(IndexField.createField("Command (Keyword)", AnalyzerType.KEYWORD, true));
        indexFields.add(IndexField.createField("Description"));
        indexFields.add(IndexField.createField("Description (Case Sensitive)", AnalyzerType.ALPHA_NUMERIC, true));
        indexFields.add(IndexField.createField("Text", AnalyzerType.ALPHA_NUMERIC));
        return indexFields;
    }
}
