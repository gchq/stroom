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
    public void testRolling() throws IOException {
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

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, StroomZipFile.SINGLE_DATA_ENTRY,
                    "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        // Roll this REPO
        proxyRepositoryManager.doRunWork();

        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {
            //ignore it as only 10ms
        }

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, StroomZipFile.SINGLE_DATA_ENTRY,
                    "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        // Roll this REPO
        proxyRepositoryManager.doRunWork();

        Assert.assertEquals(2, proxyRepositoryManager.getReadableRepository().size());
    }

    @Test
    public void testNonRolling() throws IOException {
        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(getCurrentTestDir(), "${pathId}/${id}", null);

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, StroomZipFile.SINGLE_DATA_ENTRY,
                    "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        // Nothing happens
        proxyRepositoryManager.doRunWork();

        // Same Repo
        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, StroomZipFile.SINGLE_DATA_ENTRY,
                    "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        // Nothing happens
        proxyRepositoryManager.doRunWork();

        Assert.assertEquals(1, proxyRepositoryManager.getReadableRepository().size());

    }
}
