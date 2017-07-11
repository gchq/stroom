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

package stroom.statistics.server.sql.rollup;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.util.ByteArrayUtils;
import stroom.util.test.StroomUnitTest;

import javax.xml.bind.DatatypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class TestRollUpBitMask extends StroomUnitTest {
    @Test
    public void testHex() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask.fromTagPositions(new ArrayList<Integer>());

        final byte[] bytes = rowKeyBitMap.asBytes();

        final String hex = DatatypeConverter.printHexBinary(bytes);

        System.out.println(hex);

        Assert.assertEquals("0000", hex);
    }

    @Test
    public void testToBytesAndBack() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask
                .fromMask(new short[] { 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 });

        final byte[] bytes = rowKeyBitMap.asBytes();

        System.out.println(ByteArrayUtils.byteArrayToHex(bytes));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask.fromBytes(bytes);

        System.out.println(rowKeyBitMap2.toString());

        Assert.assertEquals(rowKeyBitMap, rowKeyBitMap2);
    }

    @Test
    public void testFromTagPositions() {
        final RollUpBitMask rowKeyBitMap1 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 4, 14));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask
                .fromMask(new short[] { 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });

        Assert.assertEquals(rowKeyBitMap1, rowKeyBitMap2);
    }

    @Test
    public void testFromTagPositionsNotInOrder() {
        final RollUpBitMask rowKeyBitMap1 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 0));

        final RollUpBitMask rowKeyBitMap2 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));

        Assert.assertEquals(rowKeyBitMap1, rowKeyBitMap2);
    }

    @Test
    public void testToString() {
        final RollUpBitMask rowKeyBitMap = RollUpBitMask
                .fromMask(new short[] { 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });

        Assert.assertEquals("100000000010010", rowKeyBitMap.toString());
    }

    @Test(expected = RuntimeException.class)
    public void testFromTagPositionsInvalidPosition() {
        RollUpBitMask.fromTagPositions(Arrays.asList(1, 4, 22));
    }

    @Test(expected = RuntimeException.class)
    public void testFromMaskInvalidMask() {
        // one value too many
        RollUpBitMask.fromMask(new short[] { 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0 });
    }

    @Test(expected = RuntimeException.class)
    public void fromMaskInvalidMaskValue() {
        // one value too many
        RollUpBitMask.fromMask(new short[] { 9, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
    }

    @Test
    public void testGetRollUpPermutationsAsBooleansTagCountZero() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(0);

        Assert.assertEquals(1, perms.size());

        final List<Boolean> perm = perms.iterator().next();

        Assert.assertEquals(1, perm.size());
        Assert.assertEquals(false, perm.get(0));

    }

    @Test
    public void testGetRollUpPermutationsAsBooleansTagCountOne() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(1);

        Assert.assertEquals(2, perms.size());
        Assert.assertEquals(1, perms.iterator().next().size());
        Assert.assertEquals(1, perms.iterator().next().size());

    }

    @Test
    public void testGetRollUpPermutationsAsBooleansTagCountTwo() {
        final Set<List<Boolean>> perms = RollUpBitMask.getRollUpPermutationsAsBooleans(2);

        Assert.assertEquals(4, perms.size());

        Assert.assertTrue(perms.contains(Arrays.asList(false, false)));
        Assert.assertTrue(perms.contains(Arrays.asList(false, true)));
        Assert.assertTrue(perms.contains(Arrays.asList(true, false)));
        Assert.assertTrue(perms.contains(Arrays.asList(true, true)));

    }

    @Test
    public void testGetRollUpPermutationsAsPositionsTagCountZero() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(0);

        Assert.assertEquals(1, perms.size());
        Assert.assertTrue(perms.contains(Collections.<Integer> emptyList()));

    }

    @Test
    public void testGetRollUpPermutationsAsPositionsTagCountOne() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(1);

        Assert.assertEquals(2, perms.size());
        Assert.assertTrue(perms.contains(Collections.<Integer> emptyList()));
        Assert.assertTrue(perms.contains(Arrays.asList(0)));

    }

    @Test
    public void testGetRollUpPermutationsAsPositionsTagCountTwo() {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(2);

        Assert.assertEquals(4, perms.size());

        Assert.assertTrue(perms.contains(Collections.<Integer> emptyList()));
        Assert.assertTrue(perms.contains(Arrays.asList(1)));
        Assert.assertTrue(perms.contains(Arrays.asList(0)));
        Assert.assertTrue(perms.contains(Arrays.asList(0, 1)));

    }

    @Test
    public void testGetRollUpBitMasksTagCountZero() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(0);

        Assert.assertEquals(1, perms.size());

        final RollUpBitMask mask = perms.iterator().next();

        Assert.assertEquals("0000", mask.asHexString());
    }

    @Test
    public void testGetRollUpBitMasksTagCountOne() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(1);

        Assert.assertEquals(2, perms.size());

        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Collections.emptyList())));
        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0))));

    }

    @Test
    public void testGetRollUpBitMasksTagCountTwo() {
        final Set<RollUpBitMask> perms = RollUpBitMask.getRollUpBitMasks(2);

        Assert.assertEquals(4, perms.size());

        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Collections.emptyList())));
        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0))));
        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(1))));
        Assert.assertTrue(perms.contains(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1))));

    }

    @Test
    public void testAsShort() throws Exception {
        final short mask = 2;

        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(mask);

        Assert.assertEquals(mask, rollUpBitMask.asShort());

    }

    @Test
    public void testFromShort() throws Exception {
        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort((short) 2);

        Assert.assertEquals("0002", rollUpBitMask.asHexString());
    }

    @Test(expected = RuntimeException.class)
    public void testFromShortTooSmall() throws Exception {
        final short tooSmall = -1;

        RollUpBitMask.fromShort(tooSmall);
    }

    @Test
    public void testFromShortAllValues() {
        // check every possible value
        for (short maskVal = Short.MAX_VALUE; maskVal >= 0; maskVal--) {
            final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(maskVal);

            Assert.assertEquals(maskVal, rollUpBitMask.asShort());
        }
    }

    @Test
    public void testToBinaryString() {
        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(Arrays.asList(5, 6, 8));

        System.out.println("[" + Integer.toBinaryString(rollUpBitMask.asShort()) + "]");

    }

    @Test
    public void testGetTagPositions() throws Exception {
        // for each possible short value construct a RollUpBitMask object then
        // convert it to a list of tag positions and
        // back, asserting the two objects are equal

        for (short i = 0; i <= Short.MAX_VALUE && i >= 0; i++) {
            final RollUpBitMask rollUpBitMask = RollUpBitMask.fromShort(i);

            final SortedSet<Integer> tagPositions = rollUpBitMask.getTagPositions();

            final RollUpBitMask rollUpBitMask2 = RollUpBitMask.fromTagPositions(tagPositions);

            Assert.assertEquals(rollUpBitMask, rollUpBitMask2);

            // System.out.println(i + " - " + rollUpBitMask.toString() + " - " +
            // rollUpBitMask.getTagPositions());
        }

    }

    @Test
    public void testIsTagPositionRolledUp() {
        final List<Integer> tagPositions = Arrays.asList(0, 2, 3);

        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(tagPositions);

        // for (Integer tagPos : tagPositions) {
        // Assert.assertTrue(rollUpBitMask.isTagPositionRolledUp(tagPos));
        // }

        for (int i = 0; i <= 5; i++) {
            if (tagPositions.contains(i)) {
                Assert.assertTrue(rollUpBitMask.isTagPositionRolledUp(i));
            } else {
                Assert.assertFalse(rollUpBitMask.isTagPositionRolledUp(i));
            }
        }
    }

    @Test(expected = RuntimeException.class)
    public void testIsTagPositionRolledUp_Exception() throws Exception {
        final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2, 3));
        rollUpBitMask.isTagPositionRolledUp(-1);
    }

    @Test
    public void testConvert_AddedTags() throws Exception {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<Integer, Integer>();

        // [a,c] => [a,b,c,d] (added pos 1 and 3)

        newToOldPosMap.put(0, 0);
        newToOldPosMap.put(1, null);
        newToOldPosMap.put(2, 1);
        newToOldPosMap.put(3, null);

        final RollUpBitMask mask1 = RollUpBitMask.fromTagPositions(Collections.emptyList());
        final RollUpBitMask mask2 = RollUpBitMask.fromTagPositions(Arrays.asList(0));
        final RollUpBitMask mask3 = RollUpBitMask.fromTagPositions(Arrays.asList(1));
        final RollUpBitMask mask4 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Collections.emptyList()),
                mask1.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0)), mask2.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(2)), mask3.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)), mask4.convert(newToOldPosMap));

    }

    @Test
    public void testConvert_RemovedTags() throws Exception {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<Integer, Integer>();

        // [a,b,c,d] => [b,d] (removed pos 1 and 3)

        newToOldPosMap.put(0, 1);
        newToOldPosMap.put(1, 3);

        final RollUpBitMask mask1 = RollUpBitMask.fromTagPositions(Collections.emptyList());
        final RollUpBitMask mask2 = RollUpBitMask.fromTagPositions(Arrays.asList(0));
        final RollUpBitMask mask3 = RollUpBitMask.fromTagPositions(Arrays.asList(1));
        final RollUpBitMask mask4 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1));
        final RollUpBitMask mask5 = RollUpBitMask.fromTagPositions(Arrays.asList(0, 1, 2, 3));
        final RollUpBitMask mask6 = RollUpBitMask.fromTagPositions(Arrays.asList(1, 3));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Collections.emptyList()),
                mask1.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Collections.emptyList()),
                mask2.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0)), mask3.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0)), mask4.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1)), mask5.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1)), mask6.convert(newToOldPosMap));

    }

    @Test
    public void testConvert_ReOrdered() throws Exception {
        final Map<Integer, Integer> newToOldPosMap = new HashMap<Integer, Integer>();

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

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Collections.emptyList()),
                mask1.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(2)), mask2.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0)), mask3.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)), mask4.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(0, 1, 2)), mask5.convert(newToOldPosMap));

        Assert.assertEquals(RollUpBitMask.fromTagPositions(Arrays.asList(1, 2)), mask6.convert(newToOldPosMap));

    }

    @Test
    public void testGetBooleanMask() throws Exception {
        RollUpBitMask mask;
        List<Integer> posList;

        posList = Arrays.asList(0, 1, 2, 3, 4);
        mask = RollUpBitMask.fromTagPositions(posList);
        Assert.assertEquals(Arrays.asList(true, true, true, true, true), mask.getBooleanMask(5));

        posList = Arrays.asList(0, 2, 4);
        mask = RollUpBitMask.fromTagPositions(posList);
        Assert.assertEquals(Arrays.asList(true, false, true, false, true), mask.getBooleanMask(5));

        posList = Arrays.asList();
        mask = RollUpBitMask.fromTagPositions(posList);
        Assert.assertEquals(Arrays.asList(false, false, false, false, false), mask.getBooleanMask(5));
    }

    @Test
    public void testGetTagPositionsAsList() throws Exception {
        final List<Integer> tagPositionsInput = Arrays.asList(0, 2, 4);

        final RollUpBitMask mask = RollUpBitMask.fromTagPositions(tagPositionsInput);

        Assert.assertEquals(tagPositionsInput, mask.getTagPositionsAsList());
    }

    @Test
    public void testByteValueFromTagList() throws Exception {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tag1";

        final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);

        final byte[] expectedMaskVal = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)).asBytes();

        Assert.assertTrue(Arrays.equals(expectedMaskVal, maskVal));
    }

    @Test
    public void testByteValueFromTagList_noRollups() throws Exception {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "";

        final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);

        final byte[] expectedMaskVal = RollUpBitMask.ZERO_MASK.asBytes();

        Assert.assertTrue(Arrays.equals(expectedMaskVal, maskVal));
    }

    @Test(expected = RuntimeException.class)
    public void testByteValueFromTagList_missingTag() throws Exception {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tagBad";

        final byte[] maskVal = RollUpBitMask.byteValueFromTagList(allTags, rolledUpTags);
    }

    @Test
    public void testIntValueFromTagList() throws Exception {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tag1";

        final int maskVal = RollUpBitMask.intValueFromTagList(allTags, rolledUpTags);

        final int expectedMaskVal = RollUpBitMask.fromTagPositions(Arrays.asList(0, 2)).asShort();

        Assert.assertEquals(expectedMaskVal, maskVal);
    }

    @Test(expected = RuntimeException.class)
    public void testIntValueFromTagList_missingTag() throws Exception {
        final String allTags = "tag2,tag4,tag1,tag3";
        final String rolledUpTags = "tag3,tagBad";

        final int maskVal = RollUpBitMask.intValueFromTagList(allTags, rolledUpTags);
    }
}
