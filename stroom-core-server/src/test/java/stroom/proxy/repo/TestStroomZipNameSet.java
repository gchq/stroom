package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestStroomZipNameSet {
    @Test
    public void testScan1() {
        doTest(Arrays.asList("request.dat", "request.hdr"), true, Arrays.asList("request"));
    }

    @Test
    public void testScan2() {
        doTest(Arrays.asList("001.data", "001.ctx"), true, Arrays.asList("001"));
    }

    @Test
    public void testScan3() {
        doTest(Arrays.asList("001.dat", "002.dat", "003.dat", "002.ctx"), true,
                Arrays.asList("001.dat", "002", "003.dat"));
    }

    @Test
    public void testScan4() {
        doTest(Arrays.asList("abc.dat", "ABC.DAT", "ABC.CTX", "002.ctx"), true, Arrays.asList("abc.dat", "ABC"),
                Arrays.asList("002.ctx"));
    }

    @Test
    public void testScan5() {
        doTest(Arrays.asList("1.dat", "2.dat", "10001.dat", "10002.dat", "1.header", "2.header", "10001.hdr",
                "10002.hdr"), true, Arrays.asList("1", "2", "10001", "10002"));
    }

    @Test
    public void testScan6() {
        doTest(Arrays.asList("1", "1.hdr", "11", "11.hdr"), true, Arrays.asList("1", "11"));
    }

    @Test
    public void testScan7() {
        doTest(Arrays.asList("1", "1.ctx", "1.hdr", "11", "11.ctx", "11.hdr"), true, Arrays.asList("1", "11"));
    }

    @Test
    public void testScan8() {
        doTest(Arrays.asList("1", "11", "111", "111.hdr", "11.hdr", "1.hdr"), false, Arrays.asList("1", "11", "111"));
    }

    @Test
    public void testScanOrderFail1() {
        try {
            doTest(Arrays.asList("1", "11", "111", "111.hdr", "11.hdr", "1.hdr"), true, Arrays.asList("NA"));
            Assert.fail("Expecting exception");
        } catch (final StroomZipNameException nex) {
        }
    }

    @Test
    public void testScanOrderPass2() {
        doTest(Arrays.asList("111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr", "111.log", "11.log", "1.log"),
                true, Arrays.asList("111", "11", "1"));
    }

    @Test
    public void testScanOrderFail3() {
        try {
            doTest(Arrays.asList("111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr", "1.log", "11.log",
                    "111.log"), true, Arrays.asList("NA"));
            Assert.fail("Expecting exception");
        } catch (final StroomZipNameException nex) {
        }
    }

    @Test
    public void testScanOrderPass4() {
        doTest(Arrays.asList("111.log", "11.log", "1.log", "111.ctx", "11.ctx", "1.ctx", "111.hdr", "11.hdr", "1.hdr"),
                true, Arrays.asList("111", "11", "1"));
    }

    @Test
    public void testNotOrdered_1() {
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
        Assert.assertEquals(new HashSet<String>(expectedBaseNames), stroomZipFile.getBaseNameSet());
        for (final String dropped : droppedList) {
            for (final String baseName : stroomZipFile.getBaseNameSet()) {
                if (dropped.startsWith(baseName)) {
                    Assert.fail("Base Name Set contains dropped file");
                }
            }
        }
    }

    @Test
    public void testBaseNameTest() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        final List<StroomZipEntry> testList = new ArrayList<StroomZipEntry>();

        testList.add(stroomZipNameSet.add("1.hdr"));

        testList.add(stroomZipNameSet.add("1.ctx"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("1.data"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("2.data"));
        Assert.assertFalse(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("2.ctx"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("2.hdr"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("3.dat"));
        Assert.assertFalse(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("3.hdr"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("4.dat"));
        Assert.assertFalse(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("4.hdr"));
        Assert.assertTrue(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("5.dat"));
        Assert.assertFalse(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

        testList.add(stroomZipNameSet.add("6.dat"));
        Assert.assertFalse(testList.get(testList.size() - 2).equalsBaseName(testList.get(testList.size() - 1)));

    }

    @Test
    public void testBaseNameTest2() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        final List<StroomZipEntry> testList = new ArrayList<StroomZipEntry>();

        testList.add(stroomZipNameSet.add("SAMPLE.LOG"));

        Assert.assertEquals(1, stroomZipNameSet.getBaseNameList().size());

        Assert.assertEquals(1, stroomZipNameSet.getBaseNameGroupedList("_").size());

    }

    @Test
    public void testBaseNameGroups() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        stroomZipNameSet.add(Arrays.asList("001.hdr", "001.dat", "002_1.hdr", "002_1.dat", "002_2.hdr", "002_2.dat"));

        Assert.assertEquals(Arrays.asList(Arrays.asList("001"), Arrays.asList("002_1", "002_2")),
                stroomZipNameSet.getBaseNameGroupedList("_"));
    }

    @Test
    public void testGetBaseNameSetVsGetBaseNameList() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(false);
        stroomZipNameSet.add(Arrays.asList("005.mf", "001.hdr", "001.dat", "002_1.hdr", "002_1.dat", "003.dat", "003.mf",
                "003.meta", "002_2.hdr", "002_2.dat", "004.dat", "004.meta", "005.dat"));

        // the oder they were added in
        final List<String> expectedList = Arrays.asList("005", "001", "002_1", "003", "002_2", "004");

        final Set<String> baseNameSet = stroomZipNameSet.getBaseNameSet();
        final List<String> baseNameList = stroomZipNameSet.getBaseNameList();

        // set and list contain the same elements
        Assert.assertEquals(baseNameSet, new HashSet<>(baseNameList));

        // baseNameList is ordered in the order we added the files in
        Assert.assertEquals(expectedList, baseNameList);
    }
}
