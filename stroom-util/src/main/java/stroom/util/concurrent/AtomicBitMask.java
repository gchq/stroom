/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.concurrent;


import org.jspecify.annotations.NullMarked;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A bit mask that makes is simple to mutate individual bits atomically.
 * It supports 1 to 64 bits as it is backed by an {@link java.util.concurrent.atomic.AtomicLong}.
 * <p>
 * This was written for LmdbJava but never used. Leaving it here in case it is one
 * day useful.
 * </p>
 */
@NullMarked
public class AtomicBitMask {

    private static final int MAX_SIZE = (Long.BYTES * 8);

    private final int size;
    private final int maxIdx;
    private final AtomicLong bitMask = new AtomicLong(0);

    public AtomicBitMask() {
        this(MAX_SIZE);
    }

    public AtomicBitMask(final int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE + " (inclusive)");
        }
        this.maxIdx = size - 1;
        this.size = size;
    }

    /**
     * Flip the state of the bit at position idx.
     *
     * @return The new state of the bit.
     */
    public boolean flip(final int idx) {
        checkIdx(idx);
        final long newVal = bitMask.accumulateAndGet(idx, (currVal, idx2) ->
                currVal ^ (1L << idx2));
        return isSetWithNoCheck(newVal, idx);
    }

    /**
     * Set the bit at position idx and return the resulting bit set as a long.
     */
    public long setAndGetAsLong(final int idx) {
        checkIdx(idx);
        return bitMask.accumulateAndGet(idx, (currVal, idx2) ->
                currVal | (1L << idx2));
    }

    /**
     * Sets the bit at position idx
     *
     * @return The previous value of the set as a long.
     */
    public long getAsLongAndSet(final int idx) {
        checkIdx(idx);
        return bitMask.getAndAccumulate(idx, (currVal, idx2) ->
                currVal | (1L << idx2));
    }

    /**
     * Un-set the bit at position idx and return the resulting bit set as a long.
     */
    public long unset(final int idx) {
        checkIdx(idx);
        return bitMask.updateAndGet(currVal ->
                currVal & ~(1L << idx));
    }

    /**
     * Set/un-set the bit at position idx, according to the value of isSet,
     * and return the resulting bit set as a long.
     */
    public long setAndGetAsLong(final int idx, final boolean isSet) {
        return isSet
                ? setAndGetAsLong(idx)
                : unset(idx);
    }

    /**
     * @return True if the bit at position idx is set.
     */
    public boolean isSet(final int idx) {
        checkIdx(idx);
        return isSetWithNoCheck(bitMask.get(), idx);
    }

    /**
     * @param bitMask The m
     * @param idx
     * @return True if the bit (at position idx) in the bit mask represented by value is set.
     */
    public boolean isSet(final long bitMask, final int idx) {
        checkIdx(idx);
        return isSetWithNoCheck(bitMask, idx);
    }

    /**
     * @return The number of bits that have been set.
     */
    public int countSetBits() {
        return Long.bitCount(bitMask.get());
    }

    public int countSetBits(final long bitMask) {
        return Long.bitCount(bitMask);
    }

    /**
     * @return The number of bits that are un-set.
     */
    public int countUnSetBits() {
        return size - Long.bitCount(bitMask.get());
    }

    public int countUnSetBits(final long bitMask) {
        return size - Long.bitCount(bitMask);
    }

    public void unSetAll() {
        bitMask.set(0L);
    }

    public void setAll() {
        if (size == MAX_SIZE) {
            bitMask.set(-1L);
        } else {
            for (int i = 0; i < size; i++) {
                setAndGetAsLong(i);
            }
        }
    }

    /**
     * @return The bit mask as a long. This value can be used by the other
     * methods that take a long value, e.g. {@link AtomicBitMask#countSetBits(long)}.
     */
    public long asLong() {
        return bitMask.get();
    }

    private static boolean isSetWithNoCheck(final long bitMask, final int idx) {
        return ((bitMask >> idx) & 1L) != 0L;
    }

    private void checkIdx(final int idx) {
        if (idx < 0 || idx > maxIdx) {
            throw new IllegalArgumentException("idx must be between 0 and " + maxIdx + " (inclusive)");
        }
    }

    @Override
    public String toString() {
        return Long.toBinaryString(bitMask.get());
    }
}
