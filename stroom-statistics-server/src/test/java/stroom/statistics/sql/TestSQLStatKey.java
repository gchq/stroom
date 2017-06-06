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

package stroom.statistics.sql;

import org.junit.Before;
import org.junit.Test;
import stroom.statistics.sql.rollup.RollUpBitMaskUtil;
import stroom.statistics.sql.rollup.RollUpBitMask;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestSQLStatKey extends StroomUnitTest {
    long time = 1234L;
    String statName = "MyStatName";

    List<StatisticTag> tags = new ArrayList<>();

    @Before
    public void setup() {
        // default tag list
        tags.clear();
        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "T2V"));
    }

    @Test
    public void testConstructorTwoTags() {
        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertEquals(statName + rollUpBitMask.asHexString() + buildTagsPart(tags), sqlStatKey.getName());
    }

    @Test
    public void testConstructorNoTags() {
        tags.clear();

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertEquals(statName + rollUpBitMask.asHexString(), sqlStatKey.getName());
    }

    @Test
    public void testConstructorStarInTagName() {
        tags.clear();

        tags.add(new StatisticTag("x*x", "T1V"));
        tags.add(new StatisticTag("T2", "T2V"));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertEquals(statName + rollUpBitMask.asHexString() + buildTagsPart(tags), sqlStatKey.getName());
    }

    @Test
    public void testConstructorDelimiterInStatName() {
        tags.clear();

        final String newStatName = "My" + SQLStatisticConstants.NAME_SEPARATOR + "Stat"
                + SQLStatisticConstants.NAME_SEPARATOR + "Name";

        final SQLStatKey sqlStatKey = new SQLStatKey(time, newStatName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertEquals(
                newStatName.replaceAll(SQLStatisticConstants.NAME_SEPARATOR,
                        SQLStatisticConstants.DIRTY_CHARACTER_REPLACEMENT) + rollUpBitMask.asHexString(),
                sqlStatKey.getName());
    }

    @Test
    public void testConstructorRolledUpTag() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertEquals(statName + rollUpBitMask.asHexString() + buildTagsPart(tags), sqlStatKey.getName());
    }

    @Test
    public void testConstructorNullTagValue() {

        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", null));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertEquals(statName + rollUpBitMask.asHexString() + buildTagsPart(tags), sqlStatKey.getName());

    }

    @Test
    public void testConstructorEmptyTagValue() {

        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", ""));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertEquals(statName + rollUpBitMask.asHexString() + buildTagsPart(tags), sqlStatKey.getName());
    }

    @Test
    public void testEqualsHashCode() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final SQLStatKey sqlStatKey1 = new SQLStatKey(time, statName, tags);
        final SQLStatKey sqlStatKey2 = new SQLStatKey(new Long(time), new String(statName), new ArrayList<>(tags));

        assertTrue(sqlStatKey1.equals(sqlStatKey2));
        assertEquals(sqlStatKey1.hashCode(), sqlStatKey2.hashCode());

    }

    @Test
    public void testEqualsHashCodeFail() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final List<StatisticTag> tags2 = new ArrayList<>();

        tags2.add(new StatisticTag("T1", "*"));
        tags2.add(new StatisticTag("T2", "T2V"));

        final SQLStatKey sqlStatKey1 = new SQLStatKey(time, statName, tags);
        final SQLStatKey sqlStatKey2 = new SQLStatKey(new Long(time), new String(statName), tags2);

        assertFalse(sqlStatKey1.equals(sqlStatKey2));
        assertNotEquals(sqlStatKey1.hashCode(), sqlStatKey2.hashCode());
    }

    private String buildTagsPart(final List<StatisticTag> tags) {
        final StringBuilder sb = new StringBuilder();

        for (final StatisticTag tag : tags) {
            sb.append(SQLStatisticConstants.NAME_SEPARATOR + tag.getTag());
            final String value = tag.getValue();
            if (value == null || value.isEmpty()) {
                sb.append(SQLStatisticConstants.NAME_SEPARATOR + SQLStatisticConstants.NULL_VALUE_STRING);
            } else {
                sb.append(SQLStatisticConstants.NAME_SEPARATOR + tag.getValue());
            }
        }

        return sb.toString();
    }
}
