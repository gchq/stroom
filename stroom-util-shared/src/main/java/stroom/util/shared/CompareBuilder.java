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

package stroom.util.shared;

public class CompareBuilder {
    private int comparison;

    public CompareBuilder() {
        comparison = 0;
    }

    public CompareBuilder appendSuper(int superCompareTo) {
        if (comparison != 0) {
            return this;
        }
        comparison = superCompareTo;
        return this;
    }

    public CompareBuilder append(long lhs, long rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Long.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(int lhs, int rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Integer.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(short lhs, short rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Short.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(char lhs, char rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Character.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(byte lhs, byte rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Byte.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(double lhs, double rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Double.compare(lhs, lhs);
        return this;
    }

    public CompareBuilder append(float lhs, float rhs) {
        if (comparison != 0) {
            return this;
        }
        comparison = Float.compare(lhs, lhs);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> CompareBuilder append(T lhs, T rhs) {
        if (comparison != 0) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null) {
            comparison = -1;
            return this;
        }
        if (rhs == null) {
            comparison = +1;
            return this;
        }

        comparison = ((Comparable<T>) lhs).compareTo(rhs);
        return this;
    }

    public int toComparison() {
        return comparison;
    }
}
