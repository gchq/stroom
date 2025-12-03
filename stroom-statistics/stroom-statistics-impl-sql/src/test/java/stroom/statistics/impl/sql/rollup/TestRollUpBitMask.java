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

package stroom.statistics.impl.sql.rollup;


import stroom.bytebuffer.ByteArrayUtils;
import stroom.test.common.util.test.StroomUnitTest;

import jakarta.xml.bind.DatatypeConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRollUpBitMask extends StroomUnitTest {

    @Test
    void testHex() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask.fromTagPositions(new ArrayList<>());

        final byte[] bytes = rowKeyBitMap.asBytes();

        final String hex = DatatypeConverter.printHexBinary(bytes);

        System.out.println(hex);

        assertThat(hex).isEqualTo("0000");
    }

    @Test
    void testToBytesAndBack() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask
                .fromMask(new short[]{0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});

        final byte[] bytes = rowKeyBitMap.asBytes();

        System.out.println(ByteArrayUtils.byteArrayToHex(bytes));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask.fromBytes(bytes);

        System.out.println(rowKeyBitMap2.toString());

        assertThat(rowKeyBitMap2).isEqualTo(rowKeyBitMap);
    }

    @Test
    void testFromTagPositions() {
        final RollUpBitMask rowKeyBitMap1 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 4, 14));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask
                .fromMask(new short[]{0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});

        assertThat(rowKeyBitMap2).isEqualTo(rowKeyBitMap1);
    }

    @Test
    void testFromTagPositionsNotInOrder() {
        final RollUpBitMask rowKeyBitMap1 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 0));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));

        assertThat(rowKeyBitMap2).isEqualTo(rowKeyBitMap1);
    }

    @Test
    void testToString() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask
                .fromMask(new short[]{0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});

        assertThat(rowKeyBitMap.toString()).isEqualTo("100000000010010");
    }

    @Test
    void testFromTagPositionsInvalidPosition() {
        assertThatThrownBy(() -> {
            RollUpBitMask.fromTagPositions(Arrays.asList(1, 4, 22));
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testFromMaskInvalidMask() {
        assertThatThrownBy(() -> {
            // one value too many
            RollUpBitMask.fromMask(new short[]{0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0});
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fromMaskInvalidMaskValue() {
        assertThatThrownBy(() -> {
            // one value too many
            RollUpBitMask.fromMask(new short[]{9, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testGetRollUpPermutationsAsBooleansTagCountZero() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(0);

        assertThat(perms.size()).isEqualTo(1);

        final List<Boolean> perm = perms.iterator().next();

        assertThat(perm.size()).isEqualTo(1);
        assertThat(perm.get(0)).isEqualTo(false);

    }

    @Test
    void testGetRollUpPermutationsAsBooleansTagCountOne() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(1);

        assertThat(perms.size()).isEqualTo(2);
        assertThat(perms.iterator().next().size()).isEqualTo(1);
        assertThat(perms.iterator().next().size()).isEqualTo(1);

    }

    @Test
    void testGetRollUpPermutationsAsBooleansTagCountTwo() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(2);

        assertThat(perms.size()).isEqualTo(4);

        assertThat(perms.contains(Arrays.asList(false, false))).isTrue();
        assertThat(perms.contains(Arrays.asList(false, true))).isTrue();
        assertThat(perms.contains(Arrays.asList(true, false))).isTrue();
        assertThat(perms.contains(Arrays.asList(true, true))).isTrue();

    }

    @Test
    void testGetRollUpPermutationsAsPositionsTagCountZero() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(0);

        assertThat(perms.size()).isEqualTo(1);
        assertThat(perms.contains(Collections.<Integer>emptyList())).isTrue();

    }

    @Test
    void testGetRollUpPermutationsAsPositionsTagCountOne() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(1);

        assertThat(perms.size()).isEqualTo(2);
        assertThat(perms.contains(Collections.<Integer>emptyList())).isTrue();
        assertThat(perms.contains(Arrays.asList(0))).isTrue();

    }

    @Test
    void testGetRollUpPermutationsAsPositionsTagCountTwo() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(2);

        assertThat(perms.size()).isEqualTo(4);

        assertThat(perms.contains(Collections.<Integer>emptyList())).isTrue();
        assertThat(perms.contains(Arrays.asList(1))).isTrue();
        assertThat(perms.contains(Arrays.asList(0))).isTrue();
        assertThat(perms.contains(Arrays.asList(0, 1))).isTrue();

    }

    @Test
    void testGetRollUpBitMasksTagCountZero() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(0);

        assertThat(perms.size()).isEqualTo(1);

        final RollUpBitMask mask = perms.iterator().next();

        assertThat(mask.asHexString()).isEqualTo("0000");
    }

    @Test
    void testGetRollUpBitMasksTagCountOne() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(1);

        assertThat(perms.size()).isEqualTo(2);

        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Collections.emptyList()))).isTrue();
        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0)))).isTrue();

    }

    @Test
    void testGetRollUpBitMasksTagCountTwo() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(2);

        assertThat(perms.size()).isEqualTo(4);

        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Collections.emptyList()))).isTrue();
        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0)))).isTrue();
        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(1)))).isTrue();
        assertThat(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1)))).isTrue();

    }

    @Test
    void testAsShort() {
        final short mask = 2;

        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(mask);

        assertThat(rollUpBitMask.asShort()).isEqualTo(mask);

    }

    @Test
    void testFromShort() {
        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort((short) 2);

        assertThat(rollUpBitMask.asHexString()).isEqualTo("0002");
    }

    @Test
    void testFromShortTooSmall() {
        assertThatThrownBy(() -> {
            final short tooSmall = -1;

            RollUpBitMask.fromShort(tooSmall);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testFromShortAllValues() {
        // check every possible value
        for (short maskVal = Short.MAX_VALUE; maskVal >= 0; maskVal--) {
            final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(maskVal);

            assertThat(rollUpBitMask.asShort()).isEqualTo(maskVal);
        }
    }

    @Test
    void testToBinaryString() {
        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(Arrays.asList(5, 6, 8));

        System.out.println("[" + Integer.toBinaryString(rollUpBitMask.asShort()) + "]");

    }

    @Test
    void testGetTagPositions() {
        // for each possible short value construct a RollUpBitMask object then
        // convert it to a list of tag positions and
        // back, asserting the two objects are equal

        for (short i = 0; i <= Short.MAX_VALUE && i >= 0; i++) {
            final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(i);

            final SortedSet<Integer> tagPositions = rollUpBitMask.getTagPositions();

            final RollUpBitMask rollUpBitMask2 = RollUpBitMask.fromTagPositions(tagPositions);

            assertThat(rollUpBitMask2).isEqualTo(rollUpBitMask);

            // System.out.println(i + " - " + rollUpBitMask.toString() + " - " +
            // rollUpBitMask.getTagPositions());
        }

    }

    @Test
    void testIsTagPositionRolledUp() {
        final List<Integer> tagPositions = Arrays.asList(0, 2, 3);

        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(tagPositions);

        // for (Integer tagPos : tagPositions) {
        // assertThat(rollUpBitMask.isTagPositionRolledUp(tagPos)).isTrue();
        // }

        for (int i = 0; i <= 5; i++) {
            if (tagPositions.contains(i)) {
                assertThat(rollUpBitMask.isTagPositionRolledUp(i)).isTrue();
            } else {
                assertThat(rollUpBitMask.isTagPositionRolledUp(i)).isFalse();
            }
        }
    }

    @Test
    void testIsTagPositionRolledUp_Exception() {
        assertThatThrownBy(() -> {
            final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2, 3));
            rollUpBitMask.isTagPositionRolledUp(-1);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testConvert_AddedTags() {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<>();

        // [a,c] => [a,b,c,d] (added pos 1 and 3)

        newToOldPosMap.put(0, 0);
        newToOldPosMap.put(1, null);
        newToOldPosMap.put(2, 1);
        newToOldPosMap.put(3, null);

        final RollUpBitMask mask1 = RollUpBitMask.fromTagPositions(Collections.emptyList());
        final RollUpBitMask mask2 = RollUpBitMask.fromTagPositions(Arrays.asList(0));
        final RollUpBitMask mask3 = RollUpBitMask.fromTagPositions(Arrays.asList(1));
        final RollUpBitMask mask4 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));

        assertThat(mask1.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Collections.emptyList()));

        assertThat(mask2.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0)));

        assertThat(mask3.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(2)));

        assertThat(mask4.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)));

    }

    @Test
    void testConvert_RemovedTags() {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<>();

        // [a,b,c,d] => [b,d] (removed pos 1 and 3)

        newToOldPosMap.put(0, 1);
        newToOldPosMap.put(1, 3);

        final RollUpBitMask mask1 = RollUpBitMask.fromTagPositions(Collections.emptyList());
        final RollUpBitMask mask2 = RollUpBitMask.fromTagPositions(Arrays.asList(0));
        final RollUpBitMask mask3 = RollUpBitMask.fromTagPositions(Arrays.asList(1));
        final RollUpBitMask mask4 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));
        final RollUpBitMask mask5 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1, 2, 3));
        final RollUpBitMask mask6 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 3));

        assertThat(mask1.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Collections.emptyList()));

        assertThat(mask2.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Collections.emptyList()));

        assertThat(mask3.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0)));

        assertThat(mask4.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0)));

        assertThat(mask5.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1)));

        assertThat(mask6.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1)));

    }

    @Test
    void testConvert_ReOrdered() {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<>();

        // [a,b,c] => [b,c,a]

        newToOldPosMap.put(0, 1);
        newToOldPosMap.put(1, 2);
        newToOldPosMap.put(2, 0);

        final RollUpBitMask mask1 = RollUpBitMask.fromTagPositions(Collections.emptyList());
        final RollUpBitMask mask2 = RollUpBitMask.fromTagPositions(Arrays.asList(0));
        final RollUpBitMask mask3 = RollUpBitMask.fromTagPositions(Arrays.asList(1));
        final RollUpBitMask mask4 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));
        final RollUpBitMask mask5 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1, 2));
        final RollUpBitMask mask6 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2));

        assertThat(mask1.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Collections.emptyList()));

        assertThat(mask2.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(2)));

        assertThat(mask3.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0)));

        assertThat(mask4.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)));

        assertThat(mask5.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1, 2)));

        assertThat(mask6.convert(newToOldPosMap)).isEqualTo(RollUpBitMask.fromTagPositions(Arrays.asList(1, 2)));

    }

    @Test
    void testGetBooleanMask() {
        RollUpBitMask mask;
        List<Integer> posList;

        posList = Arrays.asList(0, 1, 2, 3, 4);
        mask = RollUpBitMask.fromTagPositions(posList);
        assertThat(mask.getBooleanMask(5)).isEqualTo(Arrays.asList(true, true, true, true, true));

        posList = Arrays.asList(0, 2, 4);
        mask = RollUpBitMask.fromTagPositions(posList);
        assertThat(mask.getBooleanMask(5)).isEqualTo(Arrays.asList(true, false, true, false, true));

        posList = Arrays.asList();
        mask = RollUpBitMask.fromTagPositions(posList);
        assertThat(mask.getBooleanMask(5)).isEqualTo(Arrays.asList(false, false, false, false, false));
    }

    @Test
    void testGetTagPositionsAsList() {
        final List<Integer> tagPositionsInput = Arrays.asList(0, 2, 4);

        final RollUpBitMask mask = RollUpBitMask.fromTagPositions(tagPositionsInput);

        assertThat(mask.getTagPositionsAsList()).isEqualTo(tagPositionsInput);
    }

    @Test
    void testByteValueFromTagList() {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tag1";

        final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);

        final byte[] expectedMaskVal = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)).asBytes();

        assertThat(Arrays.equals(expectedMaskVal, maskVal)).isTrue();
    }

    @Test
    void testByteValueFromTagList_noRollups() {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "";

        final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);

        final byte[] expectedMaskVal = RollUpBitMask.ZERO_MASK.asBytes();

        assertThat(Arrays.equals(expectedMaskVal, maskVal)).isTrue();
    }

    @Test
    void testByteValueFromTagList_missingTag() {
        assertThatThrownBy(() -> {
            final String allTags = "tag2,tag4,tag1,tag3";
            final String rolledUpTags = "tag3,tagBad";

            final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testIntValueFromTagList() {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tag1";

        final int maskVal = RollUpBitMask.intValueFromTagList(allTags, rolledUpTags);

        final int expectedMaskVal = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)).asShort();

        assertThat(maskVal).isEqualTo(expectedMaskVal);
    }

    @Test
    void testIntValueFromTagList_missingTag() {
        assertThatThrownBy(() -> {
            final String allTags = "tag2,tag4,tag1,tag3";
            final String rolledUpTags = "tag3,tagBad";

            final int maskVal = RollUpBitMask.intValueFromTagList(allTags, rolledUpTags);
        }).isInstanceOf(RuntimeException.class);
    }
}
