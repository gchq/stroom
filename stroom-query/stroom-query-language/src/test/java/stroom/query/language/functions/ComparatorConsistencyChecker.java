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

package stroom.query.language.functions;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

class ComparatorConsistencyChecker<T> {

    @SuppressWarnings("unchecked")
    void check(final List<T> list, final Comparator<T> comparator) {
        final Obj<T>[] objs = new Obj[list.size()];
        for (int i = 0; i < list.size(); i++) {
            objs[i] = new Obj<>(i, list.get(i));
        }

        for (final Obj<T> o1 : objs) {
            for (final Obj<T> o2 : objs) {
                // Ignore self.
                if (o1 != o2) {
                    final T v1 = o1.value;
                    final T v2 = o2.value;

                    final int diff = comparator.compare(v1, v2);

                    // Check inverse.
                    final int reverseDiff = comparator.compare(v2, v1);
                    if (reverseDiff * -1 != diff) {
                        throw new RuntimeException("Objects " + o1 + " and " + o2 + " are not inversely comparable.");
                    }

                    if (diff == 0) {
                        // Other object is equal.
                        o1.equal.add(o2);
                    } else if (diff > 0) {
                        // Other object is less.
                        o1.less.add(o2);
                    } else if (diff < 0) {
                        // Other object is greater.
                        o1.greater.add(o2);
                    }
                }
            }
        }

        // Now check the consistency of all objs.
        checkAllEquality(objs, comparator);
        checkAllLessThan(objs, comparator);
        checkAllGreaterThan(objs, comparator);
    }

    private void checkAllEquality(final Obj<T>[] objs, final Comparator<T> comparator) {
        for (final Obj<T> o1 : objs) {
            if (o1.equal.size() > 0) {
                // Check equals on all objects.
                for (final Obj<T> o2 : o1.equal) {
                    // Double check that we created objs correctly.
                    checkEquals(o1, o2, comparator);
                    for (final Obj<T> o3 : o2.equal) {
                        if (o2 != o3) {
                            checkEquals(o1, o3, comparator);
                        }
                    }
                }

                // Check that indexes have not been recorded as equal elsewhere.
                final Set<Obj<T>> set = new HashSet<>(o1.equal);
                for (final Obj<T> o2 : objs) {
                    if (o1 != o2) {
                        if (set.contains(o2)) {
                            final boolean changed = set.addAll(o2.equal);
                            if (changed) {
                                throw new RuntimeException(
                                        "Missing equality found between " + o1 + " and " + o2 + ".");
                            }
                        } else {
                            for (final Obj<T> o3 : o2.equal) {
                                if (set.contains(o3)) {
                                    throw new RuntimeException(
                                            "Unexpected equality found between " + o1 + " and " + o3 + ".");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkEquals(final Obj<T> o1, final Obj<T> o2, final Comparator<T> comparator) {
        final int diff = comparator.compare(o2.value, o1.value);
        if (diff != 0) {
            throw new RuntimeException("Objects " + o1 + " and " + o2 + " are not equal.");
        }

        // Check reciprocation.
        final int reverseDiff = comparator.compare(o2.value, o1.value);
        if (reverseDiff != 0) {
            throw new RuntimeException("Objects " + o2 + " and " + o1 + " are not equal.");
        }
    }

    private void checkAllLessThan(final Obj<T>[] objs, final Comparator<T> comparator) {
        for (final Obj<T> obj : objs) {
            final Set<Obj<T>> all = new HashSet<>(obj.less);

            recurseLess(objs, comparator, 1, obj, all);

//
//
//            checkStateLessThan(array, objs, comparator, i, objs[i]);
        }
    }

    private void recurseLess(final Obj<T>[] objs,
                             final Comparator<T> comparator,
                             final int depth,
                             final Obj<T> obj,
                             final Set<Obj<T>> all) {
        if (depth < 2) {
            for (final Obj<T> o : obj.less) {
                final boolean changed = all.addAll(o.less);
                if (changed) {
                    throw new RuntimeException("Unexpected child objects");
                }

                recurseLess(objs, comparator, depth + 1, o, all);
            }
        }
    }

    private void recurseGreater(final Obj<T>[] objs,
                                final Comparator<T> comparator,
                                final int depth,
                                final Obj<T> obj,
                                final Set<Obj<T>> all) {
        if (depth < 2) {
            for (final Obj<T> o : obj.greater) {
                final boolean changed = all.addAll(o.greater);
                if (changed) {
                    throw new RuntimeException("Unexpected child objects");
                }

                recurseGreater(objs, comparator, depth + 1, o, all);
            }
        }
    }

    private void checkStateLessThan(final Obj<T> rootObj, final Obj<T> parentObj, final Comparator<T> comparator) {
        if (parentObj.less.size() > 0) {
            for (final Obj<T> childObj : parentObj.less) {

                // Double check that we created objs correctly.
                checkLessThan(rootObj, childObj, comparator);

                // Recurse to check related objs.
                checkStateLessThan(rootObj, childObj, comparator);
            }
        }
    }

    private void checkLessThan(final Obj<T> o1, final Obj<T> o2, final Comparator<T> comparator) {
        final int diff = comparator.compare(o1.value, o2.value);
        if (diff < 0) {
            throw new RuntimeException("Object " + o2 + " is not less than " + o1 + ".");
        }

        // Check reciprocation.
        final int reverseDiff = comparator.compare(o2.value, o1.value);
        if (reverseDiff > 0) {
            throw new RuntimeException("Object " + o2 + " is not less than " + o1 + ".");
        }
    }

    private void checkAllGreaterThan(final Obj<T>[] objs, final Comparator<T> comparator) {
        for (final Obj<T> obj : objs) {
            final Set<Obj<T>> all = new HashSet<>(obj.greater);

            recurseGreater(objs, comparator, 1, obj, all);

//            checkStateGreaterThan(array, objs, comparator, i, objs[i]);
        }
    }

    private void checkStateGreaterThan(final Obj<T> rootObj, final Obj<T> parentObj, final Comparator<T> comparator) {
        if (parentObj.greater.size() > 0) {
            for (final Obj<T> childObj : parentObj.greater) {

                // Double check that we created objs correctly.
                checkGreaterThan(rootObj, childObj, comparator);

                // Recurse to check related objs.
                checkStateGreaterThan(rootObj, childObj, comparator);
            }
        }
    }

    private void checkGreaterThan(final Obj<T> o1, final Obj<T> o2, final Comparator<T> comparator) {
        final int diff = comparator.compare(o1.value, o2.value);
        if (diff > 0) {
            throw new RuntimeException("Object " + o2 + " is not greater than " + o1 + ".");
        }

        // Check reciprocation.
        final int reverseDiff = comparator.compare(o2.value, o1.value);
        if (reverseDiff < 0) {
            throw new RuntimeException("Object " + o2 + " is not greater than " + o1 + ".");
        }
    }

    private static class Obj<T> {

        final int index;
        final T value;
        final Set<Obj<T>> equal = new HashSet<>();
        final Set<Obj<T>> less = new HashSet<>();
        final Set<Obj<T>> greater = new HashSet<>();

        Obj(final int index, final T value) {
            this.index = index;
            this.value = value;
            equal.add(this);
        }

        @Override
        public String toString() {
            return "[" + index + " {" + value.getClass().getSimpleName() + "} - " + value + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Obj<?> obj = (Obj<?>) o;
            return index == obj.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }
}
