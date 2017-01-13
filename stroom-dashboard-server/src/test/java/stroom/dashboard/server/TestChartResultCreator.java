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

package stroom.dashboard.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.dashboard.server.vis.CompiledStructure;
import stroom.dashboard.server.vis.StructureBuilder;
import stroom.dashboard.server.vis.VisComponentResultCreator;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ItemsArrayList;
import stroom.query.ResultStore;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.Format.Type;
import stroom.query.shared.VisResult;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestChartResultCreator {
    private static final String[] SERIES_CHOICE = new String[]{"", "Test1", "Test2"};

    @Test
    public void testSeriesGen() throws Exception {
        for (int i = 0; i < 10; i++) {
            System.out.println(getSeries());
        }
    }

    @Test
    public void test() throws Exception {
        final Items<Item> items = new ItemsArrayList<Item>();
        for (int i = 0; i < 100; i++) {
            final Object[] values = new Object[3];
            values[0] = getSeries();
            values[1] = getXValue();
            values[2] = getYValue();

            final Item item = new Item(null, null, values, 0);
            items.add(item);
        }

        final Map<String, Items<Item>> childMap = new HashMap<String, Items<Item>>();
        childMap.put(null, items);

        final long size = items.size();
        final ResultStore resultStore = new ResultStore(childMap, size, size);

        final List<Field> fields = new ArrayList<Field>();
        fields.add(getField("Feed", Type.TEXT));
        fields.add(getField("X", Type.DATE_TIME));
        fields.add(getField("Y", Type.NUMBER));

        final StructureBuilder structureBuilder = new StructureBuilder(
                "{\"data\" : { \"structure\" : { \"values\" : { \"fields\" : [\"X\", \"Y\"] } } } }", "", fields);
        final CompiledStructure.Structure structure = structureBuilder.create();

        final VisComponentResultCreator resultCreator = new VisComponentResultCreator(structure);
        final VisResult res = (VisResult) resultCreator.create(resultStore, null);

        System.out.println(res.getJSON());

        Assert.assertNotNull(res.getJSON());
    }

    private Field getField(final String name, final Type type) {
        final Format format = new Format();
        format.setType(type);
        final Field field = new Field(name);
        field.setFormat(format);
        return field;
    }

    private String getSeries() {
        final int index = (int) Math.floor(Math.random() * 3D);
        return SERIES_CHOICE[index];
    }

    private double getXValue() {
        return Long.valueOf(System.currentTimeMillis() + (long) (Math.random() * 100000D)).doubleValue();
    }

    private double getYValue() {
        return Double.valueOf(Math.random() * 1000D);
    }
}
