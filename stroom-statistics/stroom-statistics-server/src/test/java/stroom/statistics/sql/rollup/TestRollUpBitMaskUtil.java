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

package stroom.statistics.sql.rollup;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.sql.StatisticTag;

import java.util.ArrayList;
import java.util.List;

public class TestRollUpBitMaskUtil {
    @Test
    public void testFromSortedTagList() {
        final List<StatisticTag> tagList = new ArrayList<>();
        tagList.add(new StatisticTag("Tag0", "someValue"));
        tagList.add(new StatisticTag("Tag1", RollUpBitMask.ROLL_UP_TAG_VALUE));
        tagList.add(new StatisticTag("Tag2", "someValue"));
        tagList.add(new StatisticTag("Tag3", RollUpBitMask.ROLL_UP_TAG_VALUE));

        final RollUpBitMask rowKeyBitMap = RollUpBitMaskUtil.fromSortedTagList(tagList);

        Assert.assertEquals("000000000001010", rowKeyBitMap.toString());
    }

    @Test
    public void testFromSortedTagListEmptyList() {
        final List<StatisticTag> tagList = new ArrayList<>();

        final RollUpBitMask rowKeyBitMap = RollUpBitMaskUtil.fromSortedTagList(tagList);

        Assert.assertEquals("000000000000000", rowKeyBitMap.toString());
    }

    @Test
    public void testFromSortedTagListNullList() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMaskUtil.fromSortedTagList(null);

        Assert.assertEquals("000000000000000", rowKeyBitMap.toString());
    }
}
