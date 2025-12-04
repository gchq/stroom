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

package stroom.statistics.impl.sql;


import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.rollup.RollUpBitMaskUtil;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSQLStatKey extends StroomUnitTest {

    private long time = 1234L;
    private String statName = "MyStatName";

    private List<StatisticTag> tags = new ArrayList<>();

    @BeforeEach
    void setup() {
        // default tag list
        tags.clear();
        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "T2V"));
    }

    @Test
    void testConstructorTwoTags() {
        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString() + buildTagsPart(tags));
    }

    @Test
    void testConstructorNoTags() {
        tags.clear();

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString());
    }

    @Test
    void testConstructorStarInTagName() {
        tags.clear();

        tags.add(new StatisticTag("x*x", "T1V"));
        tags.add(new StatisticTag("T2", "T2V"));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString() + buildTagsPart(tags));
    }

    @Test
    void testConstructorDelimiterInStatName() {
        tags.clear();

        final String newStatName = "My" + SQLStatisticConstants.NAME_SEPARATOR + "Stat"
                + SQLStatisticConstants.NAME_SEPARATOR + "Name";

        final SQLStatKey sqlStatKey = new SQLStatKey(time, newStatName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertThat(sqlStatKey.getName()).isEqualTo(newStatName.replaceAll(SQLStatisticConstants.NAME_SEPARATOR,
                SQLStatisticConstants.DIRTY_CHARACTER_REPLACEMENT) + rollUpBitMask.asHexString());
    }

    @Test
    void testConstructorRolledUpTag() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString() + buildTagsPart(tags));
    }

    @Test
    void testConstructorNullTagValue() {

        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", null));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString() + buildTagsPart(tags));

    }

    @Test
    void testConstructorEmptyTagValue() {

        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", ""));

        final SQLStatKey sqlStatKey = new SQLStatKey(time, statName, tags);

        final RollUpBitMask rollUpBitMask = RollUpBitMaskUtil.fromSortedTagList(tags);

        System.out.println(sqlStatKey.getName());

        assertThat(sqlStatKey.getName()).isEqualTo(statName + rollUpBitMask.asHexString() + buildTagsPart(tags));
    }

    @Test
    void testEqualsHashCode() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final SQLStatKey sqlStatKey1 = new SQLStatKey(time, statName, tags);
        final SQLStatKey sqlStatKey2 = new SQLStatKey(time, statName, new ArrayList<>(tags));

        assertThat(sqlStatKey1.equals(sqlStatKey2)).isTrue();
        assertThat(sqlStatKey2.hashCode()).isEqualTo(sqlStatKey1.hashCode());

    }

    @Test
    void testEqualsHashCodeFail() {
        tags.clear();

        tags.add(new StatisticTag("T1", "T1V"));
        tags.add(new StatisticTag("T2", "*"));

        final List<StatisticTag> tags2 = new ArrayList<>();

        tags2.add(new StatisticTag("T1", "*"));
        tags2.add(new StatisticTag("T2", "T2V"));

        final SQLStatKey sqlStatKey1 = new SQLStatKey(time, statName, tags);
        final SQLStatKey sqlStatKey2 = new SQLStatKey(time, statName, tags2);

        assertThat(sqlStatKey1.equals(sqlStatKey2)).isFalse();
        assertThat(sqlStatKey2.hashCode()).isNotEqualTo(sqlStatKey1.hashCode());
    }

    private String buildTagsPart(final List<StatisticTag> tags) {
        final StringBuilder sb = new StringBuilder();

        for (final StatisticTag tag : tags) {
            sb.append(SQLStatisticConstants.NAME_SEPARATOR);
            sb.append(tag.getTag());
            final String value = tag.getValue();
            if (value == null || value.isEmpty()) {
                sb.append(SQLStatisticConstants.NAME_SEPARATOR);
                sb.append(SQLStatisticConstants.NULL_VALUE_STRING);
            } else {
                sb.append(SQLStatisticConstants.NAME_SEPARATOR);
                sb.append(tag.getValue());
            }
        }

        return sb.toString();
    }
}
