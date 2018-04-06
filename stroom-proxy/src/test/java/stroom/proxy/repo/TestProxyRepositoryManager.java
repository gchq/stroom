package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.io.StreamUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;

public class TestProxyRepositoryManager extends StroomUnitTest {

//    @Before
//    public void setup() {
//        clearTestDir();
//    }

    @Test
    public void testRolling() throws IOException, InterruptedException {
        final Scheduler scheduler = new Scheduler() {
            @Override
            public boolean execute() {
                // Always run
                return true;
            }

            @Override
            public Long getScheduleReferenceTime() {
                return null;
            }
        };

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(getCurrentTestDir(), null, scheduler);

        final StroomZipRepository proxyRepository1 = proxyRepositoryManager.getActiveRepository();
        final StroomZipOutputStream stream1 = proxyRepository1.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stream1, StroomZipFile.SINGLE_DATA_ENTRY,
                "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        stream1.close();

        // Roll this REPO
        proxyRepositoryManager.doRunWork();

        Thread.sleep(10L);

        final StroomZipRepository proxyRepository2 = proxyRepositoryManager.getActiveRepository();
        final StroomZipOutputStream stream2 = proxyRepository2.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stream2, StroomZipFile.SINGLE_DATA_ENTRY,
                "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        stream2.close();

        // Roll this REPO
        proxyRepositoryManager.doRunWork();

        Assert.assertEquals(2, proxyRepositoryManager.getReadableRepository().size());
    }

    @Test
    public void testNonRolling() throws IOException {
        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(getCurrentTestDir(), "${pathId}/${id}", null);

        final StroomZipRepository proxyRepository1 = proxyRepositoryManager.getActiveRepository();
        final StroomZipOutputStream stream1 = proxyRepository1.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stream1, StroomZipFile.SINGLE_DATA_ENTRY,
                "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        stream1.close();

        // Nothing happens
        proxyRepositoryManager.doRunWork();

        // Same Repo
        final StroomZipRepository proxyRepository2 = proxyRepositoryManager.getActiveRepository();
        final StroomZipOutputStream stream2 = proxyRepository2.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(stream2, StroomZipFile.SINGLE_DATA_ENTRY,
                "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        stream2.close();

        // Nothing happens
        proxyRepositoryManager.doRunWork();

        Assert.assertEquals(1, proxyRepositoryManager.getReadableRepository().size());

    }
}
