package stroom.dashboard.expression.v1;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Disabled("Used to check comparison works as expected but long running hence ignored by default")
class TestComparator {
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
                        val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE) - (Double.MAX_VALUE / 2)));
                        break;
                }

                list.add(val);
            }

            final long now = System.currentTimeMillis();
            list.sort(new ValComparator());
            final long duration = System.currentTimeMillis() - now;
            totalDuration += duration;
            final long average = (long) (((double) totalDuration) / run);

            System.out.println("Average: " + average + "ms \tTime: " + duration + "ms");
        }
    }

    @Test
    void test2() {
        List<Val> candidateList = null;

        boolean done = false;
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
                            val = ValInteger.create(((int) (Math.random() * Integer.MAX_VALUE)) - (Integer.MAX_VALUE / 2));
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
                            val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE) - (Double.MAX_VALUE / 2)));
                            break;
                    }

                    list.add(val);
                }

                try {
                    list.sort(new ValComparator());
                } catch (final IllegalArgumentException e) {
//                    System.out.println("Found bad list: size=" + list.size());
                    // Expected this.
                    error = true;
                }
            }

            boolean exit = false;
            while (!exit) {
                final List<Val> originalList = list;
                List<Val> lower = list.subList(0, list.size() - 1);
                List<Val> upper = list.subList(1, list.size());

                // Sort each
                try {
                    new ArrayList<>(lower).sort(new ValComparator());
                    try {
                        new ArrayList<>(upper).sort(new ValComparator());
                    } catch (IllegalArgumentException e) {
//                        System.out.println("Error in upper: size=" + upper.size());
                        list = upper;
                    }
                } catch (IllegalArgumentException e) {
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

        CheckComparator.checkConsitency(candidateList, new ValComparator());
        new ComparatorConsistencyChecker<Val>().check(candidateList, new ValComparator());

        candidateList.sort(new ValComparator());
    }

    private void printList(List<Val> list) {
        System.out.println("FOUND CANDIDATE LIST (SIZE=" + list.size() + ")\n");
        for (Val val : list) {
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
                            val = ValInteger.create(((int) (Math.random() * Integer.MAX_VALUE)) - (Integer.MAX_VALUE / 2));
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
                            val = ValString.create(String.valueOf((Math.random() * Double.MAX_VALUE) - (Double.MAX_VALUE / 2)));
                            break;
                    }

                    list.add(val);
                }

                try {
                    list.sort(new ValComparator());
                } catch (final IllegalArgumentException e) {
                    System.out.println("Found bad list: size=" + list.size());
                    // Expected this.
                    error = true;
                }
            }

            boolean exit = false;
            while (!exit) {
                final List<Val> originalList = list;
                List<Val> lower = new ArrayList<>(list);
                int index = (int) (Math.random() * lower.size());
                System.out.println("Removing: " + index);
                lower.remove(index);

                // Sort each
                try {
                    lower.sort(new ValComparator());
                } catch (IllegalArgumentException e) {
                    System.out.println("Error in lower: size=" + lower.size());
                    list = lower;
                }

//                if (list == originalList) {
//                    // Start again.
//                    exit = true;

                if (list.size() < 100) {
                    System.out.println("FOUND CANDIDATE LIST:\n");
                    for (Val val : list) {
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
