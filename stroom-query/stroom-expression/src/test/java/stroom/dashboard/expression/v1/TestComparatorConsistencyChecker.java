package stroom.dashboard.expression.v1;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Disabled("Used to check comparison works as expected but long running hence ignored by default")
class TestComparatorConsistencyChecker {
    @Test
    void test() {
        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add((int) (Math.random() * Integer.MAX_VALUE));
        }

//        new ComparatorConsistencyChecker<Integer>().check(list, Comparator.naturalOrder());

        CheckComparator.checkConsitency(list, Comparator.naturalOrder());

        list.sort(Comparator.naturalOrder());
    }
}
