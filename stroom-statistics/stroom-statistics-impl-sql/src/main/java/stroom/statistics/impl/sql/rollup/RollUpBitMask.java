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

import jakarta.xml.bind.DatatypeConverter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class generates a 2 byte bit mask of length MASK_LENGTH that is used in
 * the key when the statistics are persisted. It is used to differentiate
 * statistics with identical names where different roll ups have been applied to
 * the tags. The statistic key when persisted may use any of the output forms,
 * e.g. bit string, hex string or byte array to give something like:
 * <p>
 * MyStatName7FFA
 * <p>
 * where 7FFA is the mask in hex form.
 * <p>
 * A statistic with no tags or where no tags have been rolled up will have an
 * all zero mask (0000).
 */
public class RollUpBitMask {

    // can't be any bigger than 15 as the max value for a short is (2^15 -1)
    public static final short MASK_LENGTH = 15;
    /**
     * An object for a row key with no tags or where no tags are rolled up
     */
    public static final RollUpBitMask ZERO_MASK = new RollUpBitMask((short) 0);
    public static final String ROLL_UP_TAG_VALUE = "*";
    // eternal static cache of the different permutations that have been asked
    // for so far. Objects are tiny so the
    // memory footprint should be low.
    private static final Map<Integer, Set<List<Boolean>>> permsMap = new HashMap<>();
    private static final Map<SortedSet<Integer>, RollUpBitMask> positionListToObjectMap = new HashMap<>();

    static {
        // add the case of no rollups to the map
        positionListToObjectMap.put(new TreeSet<>(), ZERO_MASK);
    }

    private final short mask;

    private RollUpBitMask(final short mask) {
        this.mask = mask;
    }

    public static byte[] shortToBytes(final short s) {
        return ByteBuffer.allocate(Short.BYTES).putShort(s).flip().array();
    }

    public static short bytesToShort(final byte[] bytes) {
        return ByteBuffer.wrap(bytes).getShort();
    }

    /**
     * @param maskValue The mask represented as a short value between 0 and 32,767. 0
     *                  represents a mask of all zeros (i.e. every field not rolled
     *                  up) and 32,767 represents a mask of all 1s (i.e. every field
     *                  rolled up)
     */
    public static RollUpBitMask fromShort(final short maskValue) {
        final int maxVal = (int) (Math.pow(2, MASK_LENGTH) - 1);
        final int minVal = 0;

        if (maskValue > maxVal || maskValue < 0) {
            throw new RuntimeException(String.format("Mask value must be between [%s] and [%s]", minVal, maxVal));
        }

        return new RollUpBitMask(maskValue);
    }

    /**
     * @param mask An array of length MASK_LENGTH containing values 0 or 1. A
     *             value of 1 at a position indicates that the tag at that
     *             position in the row key should have its values rolled up
     * @return
     */
    public static RollUpBitMask fromMask(final short[] mask) {
        if (mask.length > MASK_LENGTH) {
            throw new RuntimeException("Array is too long");
        }

        short tempMask = 0;

        final int len = mask.length;

        for (short i = 0; i < len; i++) {
            if (mask[i] == 1) {
                tempMask = setMaskValueAtPosition(i, tempMask);
            } else if (mask[i] != 0) {
                throw new RuntimeException(String.format("Value at mask position %s is not one of 1 or 0", mask[i]));
            }
        }
        return new RollUpBitMask(tempMask);
    }

    public static RollUpBitMask fromRolledupTagList(final String allTags, final String rolledUpTags) {
        if (allTags == null || allTags.length() == 0 || rolledUpTags == null || rolledUpTags.length() == 0) {
            return ZERO_MASK;
        }

        final List<String> allTagsList = Arrays.asList(allTags.split(","));
        final String[] rolledUpTagsList = rolledUpTags.split(",");
        final List<Integer> rolledUpTagPositions = new ArrayList<>();

        Collections.sort(allTagsList);

        for (final String rolledUpTag : rolledUpTagsList) {
            final int pos = allTagsList.indexOf(rolledUpTag);

            if (pos == -1) {
                throw new RuntimeException(
                        String.format("Rolled up tag [%s] could not be found in the full list of tags [%s]",
                                rolledUpTag, rolledUpTags));
            }

            rolledUpTagPositions.add(pos);
        }
        return fromTagPositions(rolledUpTagPositions);
    }

    /**
     * @param allTags      String containing all tags in any order delimited by comma,
     *                     e.g. "tag1,tag3,tag2"
     * @param rolledUpTags String containing just those tags that are to be rolled up in
     *                     any order e.g. "tag3,tag2"
     * @return The byte value of the mask
     */
    public static byte[] byteValueFromTagList(final String allTags, final String rolledUpTags) {
        return fromRolledupTagList(allTags, rolledUpTags).asBytes();
    }

    /**
     * @param allTags      String containing all tags in any order delimited by comma,
     *                     e.g. "tag1,tag3,tag2"
     * @param rolledUpTags String containing just those tags that are to be rolled up in
     *                     any order e.g. "tag3,tag2"
     * @return The int value of the mask
     */
    public static int intValueFromTagList(final String allTags, final String rolledUpTags) {
        return fromRolledupTagList(allTags, rolledUpTags).asShort();
    }

    /**
     * Generates a {@link RollUpBitMask} from the passed list of rolled up tag
     * positions. It uses a cache of tag positions to save it having to build
     * the objects each time. For that reason the passed list will be sorted to
     * ensure it can be compared to ones in the cache, i.e. [0,1] == [1,0]
     *
     * @param tagPositions A list of the array position values at which a tag is rolled
     *                     up. E.g. if you have Tag1, Tag2 & Tag3 and only Tag2 is rolled
     *                     up then [1] would be passed as only tag at position 1 is
     *                     rolled up.
     * @return The {@link RollUpBitMask} object representing the rolled up tag
     * positions.
     */
    public static RollUpBitMask fromTagPositions(final List<Integer> tagPositions) {
        return fromTagPositions(new TreeSet<>(tagPositions));
    }

    /**
     * Generates a {@link RollUpBitMask} from the passed set of rolled up tag
     * positions. It uses a cache of tag positions to save it having to build
     * the objects each time. For that reason the passed set must be sorted to
     * ensure it can be compared to ones in the cache, i.e. [0,1] == [1,0]
     *
     * @param tagPositions A sorted set of the array position values at which a tag is
     *                     rolled up. E.g. if you have Tag1, Tag2 & Tag3 and only Tag2 is
     *                     rolled up then [1] would be passed as only tag at position 1
     *                     is rolled up.
     * @return The {@link RollUpBitMask} object representing the rolled up tag
     * positions.
     */
    public static RollUpBitMask fromTagPositions(final SortedSet<Integer> tagPositions) {
        RollUpBitMask rollUpBitMask = positionListToObjectMap.get(tagPositions);

        if (rollUpBitMask == null) {
            // not in our static cache so cache the perms for this number of
            // tags

            final int maxValInList = getMaxValueInList(tagPositions);

            cacheRollUpPerms(tagPositions.size() == 0
                    ? 0
                    : (maxValInList + 1));

            // try again from the map now it should be in there
            rollUpBitMask = positionListToObjectMap.get(tagPositions);

            if (rollUpBitMask == null) {
                throw new RuntimeException(String.format("Position lists have not been loaded into the cache for " +
                                                         "a position list [%s] of size %s. This should never happen",
                        tagPositions,
                        tagPositions.size()));
            }
        }
        return rollUpBitMask;
    }

    private static int getMaxValueInList(final SortedSet<Integer> tagPositions) {
        final int result = 0;

        if (tagPositions != null) {
            return tagPositions.last();
        }
        return result;
    }

    private static RollUpBitMask buildMaskFromTagPositions(final SortedSet<Integer> tagPositions) {
        short tempMask = 0;
        if (tagPositions != null) {
            for (final Integer tagPosition : tagPositions) {
                if (tagPosition >= MASK_LENGTH || tagPosition < 0) {
                    throw new RuntimeException(
                            String.format("Passed array [%s] contains a value that is not a valid mask position [%s]",
                                    tagPositions, tagPosition));
                }
                tempMask = setMaskValueAtPosition(tagPosition, tempMask);
            }
        }

        return new RollUpBitMask(tempMask);
    }

    private static short setMaskValueAtPosition(final int position, final short existingMask) {
        // shift 1 to the left by the position in the mask
        // e.g. if you have '1' at position 2 you get 100
        final short maskForThisPos = (short) (1 << position);

        // OR the mask for this array position with the mask built up so far
        return (short) (existingMask | maskForThisPos);

        // byte[] bytes = Bytes.toBytes(maskForThisPos);
        // System.out.println(ByteArrayUtils.byteArrayToHex(bytes));
        // System.out.println(String.format("%15s",
        // Integer.toBinaryString(maskForThisPos)).replace(' ',
        // '0'));
        // System.out.println(String.format("%15s",
        // Integer.toBinaryString(tempMask)).replace(' ', '0'));

    }

    /**
     * Constructor
     *
     * @param bytes The byte array to convert from
     * @return A {@link RollUpBitMask} object built from the byte array
     */
    public static RollUpBitMask fromBytes(final byte[] bytes) {
        return new RollUpBitMask(bytesToShort(bytes));
    }

    /**
     * @param tagCount The number of tags you want permutations for
     * @return A set of {@link RollUpBitMask} objects, one for each permutation
     * of possible bit masks for that number of tags
     */
    public static Set<RollUpBitMask> getRollUpBitMasks(final int tagCount) {
        final Set<RollUpBitMask> masks = new HashSet<>();

        final Set<List<Boolean>> perms = getRollUpPermutationsAsBooleans(tagCount);

        for (final List<Boolean> perm : perms) {
            final SortedSet<Integer> tagPositions = buildPositionList(perm);

            masks.add(RollUpBitMask.fromTagPositions(tagPositions));
        }
        return masks;
    }

    /**
     * @param tagCount The number of tags you want permutations for
     * @return A set of permutations for the passed number of tags, e.g. 2 => [
     * [false,false], [false,true], [true,false], [true,true] ]
     */
    public static Set<List<Boolean>> getRollUpPermutationsAsBooleans(final int tagCount) {
        Set<List<Boolean>> resultSet = permsMap.get(tagCount);

        if (resultSet == null) {
            // not in our pre-computed map so build them and add them to the
            // cache

            // if multiple threads hit this at once then it just means some
            // brief duplicated effort, but they will both
            // get the same answer and the map will have the same values for the
            // keys being touched.
            resultSet = cacheRollUpPerms(tagCount);
        }
        return resultSet;
    }

    /**
     * @param tagCount The number of tags you want permutations for
     * @return A set of rolled up tag positions for the passed number of tags,
     * e.g. 2 => [ [], [0], [1], [0,1] ]
     */
    public static Set<List<Integer>> getRollUpPermutationsAsPositions(final int tagCount) {
        final Set<List<Boolean>> resultSet = getRollUpPermutationsAsBooleans(tagCount);

        final Set<List<Integer>> perms = new HashSet<>();

        for (final List<Boolean> perm : resultSet) {
            perms.add(new ArrayList<>(buildPositionList(perm)));
        }

        return perms;
    }

    /**
     * Converts a list that looks like [true,false,true] to a list of positions
     * of true values, i.e. [0,2]
     */
    private static SortedSet<Integer> buildPositionList(final List<Boolean> perm) {
        final SortedSet<Integer> tagPositions = new TreeSet<>();

        if (perm != null) {
            int pos = 0;
            for (final Boolean val : perm) {
                if (val) {
                    tagPositions.add(pos);
                }
                pos++;
            }
        }
        return tagPositions;
    }

    /**
     * Cache the perms in two static maps for a given number of tags
     *
     * @param tagCount
     * @return
     */
    private static Set<List<Boolean>> cacheRollUpPerms(final int tagCount) {
        final Set<List<Boolean>> permsSet = buildRollUpPermutations(tagCount);

        permsMap.put(tagCount, permsSet);

        for (final List<Boolean> perm : permsSet) {
            final SortedSet<Integer> positionList = buildPositionList(perm);

            positionListToObjectMap.put(positionList, RollUpBitMask.buildMaskFromTagPositions(positionList));
        }

        return permsSet;
    }

    private static Set<List<Boolean>> buildRollUpPermutations(final int tagCount) {
        if (tagCount < 0 || tagCount > MASK_LENGTH) {
            throw new IllegalArgumentException(String.format(
                    "buildRollUpPermutations called for too high a tagCount [%s], valid values are 0 to %s", tagCount,
                    MASK_LENGTH));
        }

        final Set<List<Boolean>> resultSet = new HashSet<>();

        if (tagCount == 0) {
            resultSet.add(Arrays.asList(Boolean.FALSE));
        } else {
            permute(new ArrayList<>(), tagCount, resultSet);
        }

        return resultSet;
    }

    /**
     * Recursive method to work out all the permutations of the tag values where
     * the value can be either true or false. True means the tag at that
     * position should be rolled up, false means it should be left as is.
     * <p>
     * colour state user
     * <p>
     * false false true
     * <p>
     * true false true
     * <p>
     * true true false
     * <p>
     * true false false
     * <p>
     * false true false
     * <p>
     * false true true
     * <p>
     * false false false
     * <p>
     * true true true
     * <p>
     * <p>
     * <p>
     * On each recursive iteration it fans out by two, each time building up the
     * soFar list until it has filled all positions.
     *
     * @param soFar      This is the list of boolean values that gets built up as it
     *                   goes along. Top level call should pass in an empty list.
     * @param iterations Top level call should pass in the size of the source list to
     *                   permute
     * @param resultSet  The set of complete permutations that gets built up as each
     *                   one is found.
     */
    private static void permute(final List<Boolean> soFar, final int iterations, final Set<List<Boolean>> resultSet) {
        if (iterations == 0) {
            // got to the end so add soFar to the result set

            resultSet.add(new ArrayList<>(soFar));

        } else {
            permute(addToList(soFar, false), iterations - 1, resultSet);

            permute(addToList(soFar, true), iterations - 1, resultSet);
        }
    }

    private static List<Boolean> addToList(final List<Boolean> list, final Boolean element) {
        final List<Boolean> tempList = new ArrayList<>(list);

        tempList.add(element);
        return tempList;
    }

    /**
     * @return The bit mask as a list of field positions where the field is
     * rolled up, e.g. [0,2] indicates that the fields at position 0 and
     * 2 are rolled up
     */
    public SortedSet<Integer> getTagPositions() {
        final SortedSet<Integer> tagPositions = new TreeSet<>();

        populateTagPositionCollection(tagPositions);

        return tagPositions;
    }

    public List<Integer> getTagPositionsAsList() {
        final List<Integer> tagPositions = new ArrayList<>();

        populateTagPositionCollection(tagPositions);

        return tagPositions;
    }

    private void populateTagPositionCollection(final Collection<Integer> collection) {
        // convert the mask value into a string of 1s and 0s, with position zero
        // being on the right.
        final String bitMask = Integer.toBinaryString(mask);

        for (int strPos = bitMask.length() - 1; strPos >= 0; strPos--) {
            final int tagPos = bitMask.length() - 1 - strPos;
            if (bitMask.charAt(strPos) == '1') {
                collection.add(tagPos);
            }
        }
    }

    public List<Boolean> getBooleanMask(final int tagCount) {
        // convert the mask value into a string of 1s and 0s, with position zero
        // being on the right.
        final String bitMask = Integer.toBinaryString(mask);

        final List<Boolean> booleanMask = new ArrayList<>();

        for (int strPos = bitMask.length() - 1; strPos >= 0; strPos--) {
            booleanMask.add(bitMask.charAt(strPos) == '1');
        }

        // pad the mask out with false values as the binary string mask may be
        // shorter than the number of tags we have
        while (booleanMask.size() < tagCount) {
            booleanMask.add(false);
        }
        return booleanMask;
    }

    public boolean isTagPositionRolledUp(final int tagPosition) {
        if (tagPosition < 0) {
            throw new RuntimeException("Field position cannot be less than zero");
        }

        return getTagPositions().contains(tagPosition);
    }

    /**
     * Converts one {@link RollUpBitMask} object into another using the passed
     * map of new tag positions to old tag positions. If an old tag position has
     * no corresponding new position, then that tag cannot be rolled up.
     *
     * @param newToOldFieldPositionMap Map containing keys which are the positions of all the new
     *                                 tags and values which are the old tag position that the new
     *                                 tag position corresponds to.
     * @return A new {@link RollUpBitMask} object
     */
    public RollUpBitMask convert(final Map<Integer, Integer> newToOldFieldPositionMap) {
        Objects.requireNonNull(newToOldFieldPositionMap);

        final Set<Integer> rolledUpFieldPositions = new HashSet<>();

        for (final Integer newPos : newToOldFieldPositionMap.keySet()) {
            // work out what the old field position was
            final Integer oldPos = newToOldFieldPositionMap.get(newPos);

            if (oldPos != null && this.isTagPositionRolledUp(oldPos)) {
                // the old position that corresponds to the new position was
                // rolled up so add the new position
                rolledUpFieldPositions.add(newPos);
            }
        }

        return RollUpBitMask.fromTagPositions(new TreeSet<>(rolledUpFieldPositions));
    }

    public short asShort() {
        return this.mask;
    }

    /**
     * @return The mask as a byte array
     */
    public byte[] asBytes() {
        return shortToBytes(mask);
    }

    public String asHexString() {
        return DatatypeConverter.printHexBinary(asBytes());
    }

    /**
     * Output has mask position zero on the right
     */
    @Override
    public String toString() {
        return String.format("%" + MASK_LENGTH + "s", Integer.toBinaryString(this.mask))
                .replace(' ', '0');
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RollUpBitMask that = (RollUpBitMask) o;
        return mask == that.mask;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mask);
    }
}
