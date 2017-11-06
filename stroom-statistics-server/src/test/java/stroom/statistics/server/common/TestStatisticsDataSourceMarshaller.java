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

package stroom.statistics.server.common;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TestStatisticsDataSourceMarshaller extends StroomUnitTest {
    @Test
    public void testUnmarshall() {
        StatisticStoreEntity statisticsDataSource = new StatisticStoreEntity();

        //put the fields in the xml out of order to check that they are stored in order
        //in the object
        String str = "";
        str += "<?xml version=\"1.1\" encoding=\"UTF-8\"?>";
        str += "<data>";
        str += " <field>";
        str += "  <fieldName>user</fieldName>";
        str += " </field>";
        str += " <field>";
        str += "  <fieldName>feed</fieldName>";
        str += " </field>";
        str += " <customRollUpMask>";
        str += " </customRollUpMask>";
        str += " <customRollUpMask>";
        str += "  <rolledUpTagPosition>0</rolledUpTagPosition>";
        str += "  <rolledUpTagPosition>1</rolledUpTagPosition>";
        str += "  <rolledUpTagPosition>2</rolledUpTagPosition>";
        str += " </customRollUpMask>";
        str += " <customRollUpMask>";
        str += "  <rolledUpTagPosition>1</rolledUpTagPosition>";
        str += " </customRollUpMask>";
        str += " <customRollUpMask>";
        str += "  <rolledUpTagPosition>0</rolledUpTagPosition>";
        str += "  <rolledUpTagPosition>1</rolledUpTagPosition>";
        str += " </customRollUpMask>";
        str += "</data>";

        statisticsDataSource.setData(str);

        final StatisticsDataSourceMarshaller marshaller = new StatisticsDataSourceMarshaller();

        statisticsDataSource = marshaller.unmarshal(statisticsDataSource);

        Assert.assertNotNull(statisticsDataSource.getStatisticDataSourceDataObject());

        final List<StatisticField> fields = statisticsDataSource.getStatisticDataSourceDataObject()
                .getStatisticFields();
        final Set<CustomRollUpMask> masks = statisticsDataSource.getStatisticDataSourceDataObject()
                .getCustomRollUpMasks();

        Assert.assertEquals(2, fields.size());
        Assert.assertEquals(4, masks.size());
        Assert.assertEquals("feed", fields.iterator().next().getFieldName());

        Assert.assertTrue(masks.contains(buildMask()));
        Assert.assertTrue(masks.contains(buildMask(0, 1, 2)));
        Assert.assertTrue(masks.contains(buildMask(1)));
        Assert.assertTrue(masks.contains(buildMask(0, 1)));

        // make sure the field positions are being cached when the object is
        // unmarshalled
        Assert.assertEquals(0, statisticsDataSource.getPositionInFieldList("feed").intValue());
        Assert.assertEquals(1, statisticsDataSource.getPositionInFieldList("user").intValue());

    }

    private CustomRollUpMask buildMask(final int... positions) {
        final List<Integer> list = new ArrayList<Integer>();

        for (final int pos : positions) {
            list.add(pos);
        }

        return new CustomRollUpMask(list);
    }
}
