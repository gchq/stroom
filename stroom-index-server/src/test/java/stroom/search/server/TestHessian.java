package stroom.search.server;

import com.caucho.hessian.io.Hessian2Output;
import org.junit.Test;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.StaticValueFunction;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.mapreduce.v2.UnsafePairQueue;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.query.api.v2.DateTimeFormat;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.GroupKey;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.query.common.v2.TablePayload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestHessian {
    @Test
    public void testClusterSearchTask() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addOperator(
                        new ExpressionOperator.Builder()
                                .addTerm("test", Condition.BETWEEN, "test")
                                .build())
                .addTerm("test", Condition.CONTAINS, "test")
                .build();

        final Query query = new Query.Builder()
                .dataSource("test", "test", "test")
                .addParam("test", "test")
                .expression(expression)
                .build();

        final Rack rack = Rack.create("rack");
        final Node node = Node.create(rack, "node");

        final IndexFields indexFields = createIndexFields();
        final IndexField[] fields = indexFields.getIndexFields().toArray(new IndexField[0]);

        final Field field = new Field(
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
        final CoprocessorSettings coprocessorSettings = new TableCoprocessorSettings(tableSettings);
        final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap = new HashMap<>();
        coprocessorMap.put(coprocessorKey, coprocessorSettings);

        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask("test",
                "test",
                query,
                Arrays.asList(1L, 2L, 3L),
                node,
                fields,
                1000,
                coprocessorMap,
                "locale",
                1000);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);

        out.writeObject(clusterSearchTask);
        out.close();
    }

    @Test
    public void testNodeResult() throws IOException {
        final GroupKey key = new GroupKey(ValString.create("test"));
        final UnsafePairQueue<GroupKey, Item> pairQueue = new UnsafePairQueue<>();
        pairQueue.collect(key, new Item(key, new Generator[]{getGenerator("v1"), getGenerator("v2")}, 0));
        pairQueue.collect(key, new Item(key, new Generator[]{getGenerator("v4"), getGenerator("v6")}, 0));
        pairQueue.collect(key, new Item(key, new Generator[]{getGenerator("v7"), getGenerator("v8")}, 0));
        final CoprocessorKey coprocessorKey = new CoprocessorKey(100, new String[]{"c1, c2"});
        final TablePayload tablePayload = new TablePayload(pairQueue);
        final Map<CoprocessorKey, Payload> payloadMap = new HashMap<>();
        payloadMap.put(coprocessorKey, tablePayload);

        final NodeResult nodeResult = new NodeResult(payloadMap, Arrays.asList("test"), true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(baos);

        out.writeObject(nodeResult);
        out.close();
    }

    private Generator getGenerator(final String string) {
        return new StaticValueFunction(ValString.create(string)).createGenerator();
    }

    private IndexFields createIndexFields() {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
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
