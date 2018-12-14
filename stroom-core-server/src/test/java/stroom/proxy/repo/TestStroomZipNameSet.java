package stroom.proxy.repo;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestStroomZipNameSet {
    @Test
    void testScan1() {
        doTest(Arrays.asList("request.dat", "request.hdr"), true, Arrays.asList("request"));
    }

    @Test
    void testScan2() {
        doTest(Arrays.asList("001.data", "001.ctx"), true, Arrays.asList("001"));
    }

    @Test
    void testScan3() {
        doTest(Arrays.asList("001.dat", "002.dat", "003.dat", "002.ctx"), true,
                Arrays.asList("001.dat", "002", "003.dat"));
    }

    @Test
    void testScan4() {
        doTest(Arrays.asList("abc.dat", "ABC.DAT", "ABC.CTX", "002.ctx"), true, Arrays.asList("abc.dat", "ABC"),
                Arrays.asList("002.ctx"));
    }

    @Test
    void testScan5() {
        doTest(Arrays.asList("1.dat", "2.dat", "10001.dat", "10002.dat", "1.header", "2.header", "10001.hdr",
                "10002.hdr"), true, Arrays.asList("1", "2", "10001", "10002"));
    }

    @Test
    void testScan6() {
        doTest(Arrays.asList("1", "1.hdr", "11", "11.hdr"), true, Arrays.asList("1", "11"));
    }

    @Test
    void testScan7() {
        doTest(Arrays.asList("1", "1.ctx", "1.hdr", "11", "11.ctx", "11.hdr"), true, Arrays.asList("1", "11"));
    }

    @Test
    void testScan8() {
        doTest(Arrays.asList("1", "11", "111", "111.hdr", "11.hdr", "1.hdr"), false, Arrays.asList("1", "11", "111"));
    }

    @Test
    void testScanOrderFail1() {
        try {
            doTest(Arrays.asList("1", "11", "111", "111.hdr", "11.hdr", "1.hdr"), true, Arrays.asList("NA"));
            fail("Expecting exception");
        } catch (final StroomZipNameException nex) {
        }
    }

    @Test
    void testScanOrderPass2() {
        doTest(Arrays.asList("111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr", "111.log", "11.log", "1.log"),
                true, Arrays.asList("111", "11", "1"));
    }

    @Test
    void testScanOrderFail3() {
        try {
            doTest(Arrays.asList("111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr", "1.log", "11.log",
                    "111.log"), true, Arrays.asList("NA"));
            fail("Expecting exception");
        } catch (final StroomZipNameException nex) {
        }
    }

    @Test
    void testScanOrderPass4() {
        doTest(Arrays.asList("111.log", "11.log", "1.log", "111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr"),
                true, Arrays.asList("111", "11", "1"));
    }

    @Test
    void testNotOrdered_1() {
        doTest(Arrays.asList("2.dat", "1.dat", "2.meta", "1.meta"), false, Arrays.asList("1", "2"));
    }

    private void doTest(final List<String> zipContents, final boolean orderCheck,
                        final List<String> expectedBaseNames) {
        doTest(zipContents, orderCheck, expectedBaseNames, new ArrayList<String>());
    }

    private void doTest(final List<String> zipContents, final boolean orderCheck, final List<String> expectedBaseNames,
                        final List<String> droppedList) {
        final StroomZipNameSet stroomZipFile = new StroomZipNameSet(orderCheck);
        stroomZipFile.add(zipContents);
        assertThat(stroomZipFile.getBaseNameSet()).isEqualTo(new HashSet<String>(expectedBaseNames));
        for (final String dropped : droppedList) {
            for (final String baseName : stroomZipFile.getBaseNameSet()) {
                if (dropped.startsWith(baseName)) {
                    fail("Base Name Set contains dropped file");
                }
            }
        }
    }

    @Test
    void testBaseNameTest() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        final List<StroomZipEntry> testList = new ArrayList<StroomZipEntry>();

        testList.add(stroomZipNameSet.add("1.hdr"));

        testList.add(stroomZipNameSet.add("1.ctx"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("1.data"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("2.data"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isFalse();

        testList.add(stroomZipNameSet.add("2.ctx"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("2.hdr"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("3.dat"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isFalse();

        testList.add(stroomZipNameSet.add("3.hdr"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("4.dat"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isFalse();

        testList.add(stroomZipNameSet.add("4.hdr"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isTrue();

        testList.add(stroomZipNameSet.add("5.dat"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isFalse();

        testList.add(stroomZipNameSet.add("6.dat"));
        assertThat(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1))).isFalse();

    }

    @Test
    void testBaseNameTest2() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        final List<StroomZipEntry> testList = new ArrayList<StroomZipEntry>();

        testList.add(stroomZipNameSet.add("SAMPLE.LOG"));

        assertThat(stroomZipNameSet.getBaseNameList().size()).isEqualTo(1);

        assertThat(stroomZipNameSet.getBaseNameGroupedList("_").size()).isEqualTo(1);

    }

    @Test
    void testBaseNameGroups() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        stroomZipNameSet.add(Arrays.asList("001.hdr", "001.dat", "002_1.hdr", "002_1.dat", "002_2.hdr", "002_2.dat"));

        assertThat(stroomZipNameSet.getBaseNameGroupedList("_")).isEqualTo(Arrays.asList(Arrays.asList("001"), Arrays.asList("002_1", "002_2")));
    }

    @Test
    void testGetBaseNameSetVsGetBaseNameList() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(false);
        stroomZipNameSet.add(Arrays.asList("005.mf", "001.hdr", "001.dat", "002_1.hdr", "002_1.dat", "003.dat", "003.mf",
                "003.meta", "002_2.hdr", "002_2.dat", "004.dat", "004.meta", "005.dat"));

        // the oder they were added in
        final List<String> expectedList = Arrays.asList("005", "001", "002_1", "003", "002_2", "004");

        final Set<String> baseNameSet = stroomZipNameSet.getBaseNameSet();
        final List<String> baseNameList = stroomZipNameSet.getBaseNameList();

        // set and list contain the same elements
        assertThat(new HashSet<>(baseNameList)).isEqualTo(baseNameSet);

        // baseNameList is ordered in the order we added the files in
        assertThat(baseNameList).isEqualTo(expectedList);
    }
}
