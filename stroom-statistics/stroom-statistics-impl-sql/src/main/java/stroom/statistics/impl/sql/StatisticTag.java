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

import java.io.Serializable;
import java.util.Comparator;

public class StatisticTag implements Serializable, Comparable<StatisticTag> {

    public static final String NULL_VALUE_STRING = "<<<<NULL_VALUE>>>>";
    private static final long serialVersionUID = 1647083366837339057L;
    private final String tag;
    private final String value;

    private final int hashCode;

    public StatisticTag(final String tag, final String value) {
        if (tag == null || tag.length() == 0) {
            throw new RuntimeException("Cannot have a statistic tag with null or zero length name");
        }
        this.tag = tag;
        this.value = value;
        hashCode = buildHashCode();
    }

    public String getTag() {
        return tag;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int buildHashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tag == null)
                ? 0
                : tag.hashCode());
        result = prime * result + ((value == null)
                ? 0
                : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof StatisticTag)) {
            return false;
        }
        final StatisticTag other = (StatisticTag) obj;
        if (tag == null) {
            if (other.getTag() != null) {
                return false;
            }
        } else if (!tag.equals(other.getTag())) {
            return false;
        }
        if (value == null) {
            return other.getValue() == null;
        } else {
            return value.equals(other.getValue());
        }
    }

    @Override
    public int compareTo(final StatisticTag o) {
        return this.toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return "StatisticTag [tag=" + tag + ", value=" + value + "]";
    }

    public static class TagNameComparator implements Comparator<StatisticTag> {

        @Override
        public int compare(final StatisticTag tag1, final StatisticTag tag2) {
            if (tag1 == null || tag2 == null) {
                throw new RuntimeException("Tag list contains null elements, this should not happen");
            }
            // compare on just the tag name, not the value as tag name should be
            // unique in the list.
            // Tag name cannot be null.
            return tag1.getTag().compareTo(tag2.getTag());
        }
    }
}
