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


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Disabled("Used to check comparison works as expected but long running hence ignored by default")
class TestComparator {

    private static final Comparator<Val> COMPARATOR = ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR;

    @Test
    void test() {
        long totalDuration = 0;
        for (int run = 1; run <= 1000000; run++) {
            final List<Val> list = new ArrayList<>();

            for (int i = 0; i < 1000000; i++) {
                final int selector = (int) (Math.random() * 8);
                Val val = null;

                switch (selector) {
                    case 0:
                        val = ValNull.INSTANCE;
                        break;
                    case 1:
                        val = ValErr.create("Error");
                        break;
                    case 2:
                        val = ValInteger.create(((int) (Math.random() * Integer.MAX_VALUE)) - (Integer.MAX_VALUE / 2));
                        break;
                    case 3:
                        val = ValDouble.create((Math.random() * Double.MAX_VALUE) - (Double.MAX_VALUE / 2));
                        break;
                    case 4:
                        val = ValLong.create(((long) (Math.random() * Long.MAX_VALUE)) - (Long.MAX_VALUE / 2));
                        break;
                    case 5:
                        val = ValBoolean.create(Math.random() > 0.5);
                        break;
                    case 6:
                        val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE)
                                - (Double.MAX_VALUE / 2)));
                        break;
                }

                list.add(val);
            }

            final long now = System.currentTimeMillis();
            list.sort(COMPARATOR);
            final long duration = System.currentTimeMillis() - now;
            totalDuration += duration;
            final long average = (long) (((double) totalDuration) / run);

            System.out.println("Average: " + average + "ms \tTime: " + duration + "ms");
        }
    }

    @Test
    void test2() {
        List<Val> candidateList = null;

        final boolean done = false;
        for (int round = 0; round < 10 && !done; round++) {
            boolean error = false;
            List<Val> list = null;

            for (int j = 0; j < 100000 && !error; j++) {
                list = new ArrayList<>();

                for (int i = 0; i < 1000; i++) {
                    final int selector = (int) (Math.random() * 8);
                    Val val = null;

                    switch (selector) {
                        case 0:
                            val = ValNull.INSTANCE;
                            break;
                        case 1:
                            val = ValErr.create("Error");
                            break;
                        case 2:
                            val = ValInteger.create(((int) (Math.random() * Integer.MAX_VALUE))
                                    - (Integer.MAX_VALUE / 2));
                            break;
                        case 3:
                            val = ValDouble.create(Math.random());
                            break;
                        case 4:
                            val = ValLong.create(((long) (Math.random() * Long.MAX_VALUE)) - (Long.MAX_VALUE / 2));
                            break;
                        case 5:
                            val = ValBoolean.create(Math.random() > 0.5);
                            break;
                        case 6:
                            val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE)
                                    - (Double.MAX_VALUE / 2)));
                            break;
                    }

                    list.add(val);
                }

                try {
                    list.sort(COMPARATOR);
                } catch (final IllegalArgumentException e) {
//                    System.out.println("Found bad list: size=" + list.size());
                    // Expected this.
                    error = true;
                }
            }

            boolean exit = false;
            while (!exit) {
                final List<Val> originalList = list;
                final List<Val> lower = list.subList(0, list.size() - 1);
                final List<Val> upper = list.subList(1, list.size());

                // Sort each
                try {
                    new ArrayList<>(lower).sort(COMPARATOR);
                    try {
                        new ArrayList<>(upper).sort(COMPARATOR);
                    } catch (final IllegalArgumentException e) {
//                        System.out.println("Error in upper: size=" + upper.size());
                        list = upper;
                    }
                } catch (final IllegalArgumentException e) {
//                    System.out.println("Error in lower: size=" + lower.size());
                    list = lower;
                }

                if (list == originalList) {
                    // Start again.
                    exit = true;

                    if (candidateList == null) {
                        candidateList = list;
                        printList(candidateList);
                    } else if (candidateList.size() > list.size()) {
                        candidateList = list;
                        printList(candidateList);
                    }

//                    if (list.size() < 72) {
//                        System.out.println("FOUND CANDIDATE LIST SIZE:\n");
//                        for (Val val : list) {
//                            if (val == null) {
//                                System.out.println("NULL");
//                            } else {
//                                System.out.println(val.getClass().getSimpleName() + ": " + val.toString());
//                            }
//                        }
//                        done = true;
//                    }
                }
            }
        }

        CheckComparator.checkConsitency(candidateList, COMPARATOR);
        new ComparatorConsistencyChecker<Val>().check(candidateList, COMPARATOR);

        candidateList.sort(COMPARATOR);
    }

    private void printList(final List<Val> list) {
        System.out.println("FOUND CANDIDATE LIST (SIZE=" + list.size() + ")\n");
        for (final Val val : list) {
            if (val == null) {
                System.out.println("NULL");
            } else {
                System.out.println(val.getClass().getSimpleName() + ": " + val.toString());
            }
        }
        System.out.println("\n\n");
    }

    @Test
    void test3() {
        boolean done = false;
        for (int round = 0; round < 100 && !done; round++) {
            boolean error = false;
            List<Val> list = null;

            for (int j = 0; j < 100000 && !error; j++) {
                list = new ArrayList<>();

                for (int i = 0; i < 1000; i++) {
                    final int selector = (int) (Math.random() * 7);
                    Val val = null;

                    switch (selector) {
                        case 0:
                            val = ValNull.INSTANCE;
                            break;
                        case 1:
                            val = ValErr.create("Error");
                            break;
                        case 2:
                            val = ValInteger.create(((int) (Math.random() * Integer.MAX_VALUE))
                                    - (Integer.MAX_VALUE / 2));
                            break;
                        case 3:
                            val = ValDouble.create(Math.random());
                            break;
                        case 4:
                            val = ValLong.create(((long) (Math.random() * Long.MAX_VALUE)) - (Long.MAX_VALUE / 2));
                            break;
                        case 5:
                            val = ValBoolean.create(Math.random() > 0.5);
                            break;
                        case 6:
                            val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE)
                                    - (Double.MAX_VALUE / 2)));
                            break;
                    }

                    list.add(val);
                }

                try {
                    list.sort(COMPARATOR);
                } catch (final IllegalArgumentException e) {
                    System.out.println("Found bad list: size=" + list.size());
                    // Expected this.
                    error = true;
                }
            }

            boolean exit = false;
            while (!exit) {
                final List<Val> originalList = list;
                final List<Val> lower = new ArrayList<>(list);
                final int index = (int) (Math.random() * lower.size());
                System.out.println("Removing: " + index);
                lower.remove(index);

                // Sort each
                try {
                    lower.sort(COMPARATOR);
                } catch (final IllegalArgumentException e) {
                    System.out.println("Error in lower: size=" + lower.size());
                    list = lower;
                }

//                if (list == originalList) {
//                    // Start again.
//                    exit = true;

                if (list.size() < 100) {
                    System.out.println("FOUND CANDIDATE LIST:\n");
                    for (final Val val : list) {
                        if (val == null) {
                            System.out.println("NULL");
                        } else {
                            System.out.println(val.getClass().getSimpleName() + ": " + val.toString());
                        }
                    }
                    exit = true;
                    done = true;
                }
//                }
            }
        }
    }
}
