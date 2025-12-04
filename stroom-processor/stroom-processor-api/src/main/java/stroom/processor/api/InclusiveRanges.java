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

package stroom.processor.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InclusiveRanges {

    private final List<InclusiveRange> ranges;

    public InclusiveRanges() {
        this(new ArrayList<>());
    }

    private InclusiveRanges(final List<InclusiveRange> ranges) {
        this.ranges = ranges;
    }

    public static List<InclusiveRange> rangesFromString(final String string) {
        final List<InclusiveRange> ranges = new ArrayList<>();
        if (string != null) {
            final String[] parts = string.split(",");
            for (final String part : parts) {
                final String[] fromAndTo = part.split(">");
                if (fromAndTo.length == 1) {
                    final Long from = Long.valueOf(fromAndTo[0]);
                    final InclusiveRange range = new InclusiveRange(from, from);
                    ranges.add(range);
                } else if (fromAndTo.length == 2) {
                    final Long from = Long.valueOf(fromAndTo[0]);
                    final Long to = Long.valueOf(fromAndTo[1]);
                    final InclusiveRange range = new InclusiveRange(from, to);
                    ranges.add(range);
                }
            }
        }
        return ranges;
    }

    public void addEvent(final long num) {
        boolean found = false;
        for (int i = 0; !found && i < ranges.size(); i++) {
            final InclusiveRange range = ranges.get(i);
            if (range.min < num) {
                if (range.max >= num) {
                    found = true;
                } else if (range.max + 1 == num) {
                    if (ranges.size() > i + 1 && ranges.get(i + 1).min - 1 == num) {
                        ranges.set(i, new InclusiveRange(range.min, ranges.get(i + 1).max));
                        ranges.remove(i + 1);
                    } else {
                        ranges.set(i, new InclusiveRange(range.min, num));
                    }
                    found = true;
                }
            } else if (range.min > num) {
                if (range.min == num + 1) {
                    ranges.set(i, new InclusiveRange(num, range.max));
                } else {
                    ranges.add(i, new InclusiveRange(num, num));
                }
                found = true;

            } else if (range.min == num) {
                found = true;
            }
        }
        if (!found) {
            ranges.add(new InclusiveRange(num, num));
        }
    }

    public InclusiveRanges subRanges(final int size) {
        return new InclusiveRanges(ranges.subList(0, size));
    }

    public List<InclusiveRange> getRanges() {
        return ranges;
    }

    public long count() {
        long count = 0;
        if (ranges != null) {
            for (final InclusiveRange range : ranges) {
                count += (range.max - range.min) + 1;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return rangesToString();
    }

    public String rangesToString() {
        final StringBuilder sb = new StringBuilder();
        for (final InclusiveRange range : ranges) {
            if (range.min == range.max) {
                sb.append(range.min);
            } else {
                sb.append(range.min);
                sb.append(">");
                sb.append(range.max);
            }
            sb.append(",");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public InclusiveRange getOuterRange() {
        if (ranges.size() == 0) {
            return null;
        }

        final InclusiveRange start = ranges.get(0);
        final InclusiveRange end = ranges.get(ranges.size() - 1);
        return new InclusiveRange(start.min, end.max);
    }

    public static class InclusiveRange {

        private final long min;
        private final long max;

        public InclusiveRange(final long min, final long max) {
            this.min = min;
            this.max = max;
        }

        public static InclusiveRange create(final long min, final long max) {
            return new InclusiveRange(min, max);
        }

        public static InclusiveRange create(final long num) {
            return new InclusiveRange(num, num);
        }

        public static InclusiveRange extend(final InclusiveRange existing, final long num) {
            if (existing == null) {
                return create(num);
            }

            if (num < existing.min) {
                return new InclusiveRange(num, existing.max);
            } else if (num > existing.max) {
                return new InclusiveRange(existing.min, num);
            }

            return existing;
        }

        public static InclusiveRange extend(final InclusiveRange existing, final long min, final long max) {
            if (existing == null) {
                return create(min, max);
            }

            if (min < existing.min && max > existing.max) {
                return new InclusiveRange(min, max);
            } else if (min < existing.min) {
                return new InclusiveRange(min, existing.max);
            } else if (max > existing.max) {
                return new InclusiveRange(existing.min, max);
            }

            return existing;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final InclusiveRange that = (InclusiveRange) o;
            return min == that.min && max == that.max;
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(min);
            sb.append("..");
            sb.append(max);
            sb.append("]");
            return sb.toString();
        }
    }
}
