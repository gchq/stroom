package stroom.proxy.repo;

import org.junit.jupiter.api.Test;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipOutputStream;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.StreamUtil;
import stroom.util.scheduler.Scheduler;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyRepositoryManager extends StroomUnitTest {
    @Test
    void testRolling() throws IOException, InterruptedException {
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

        Thread.sleep(10L);

        try (final StroomZipOutputStream stream = proxyRepositoryManager.getStroomZipOutputStream()) {
            StroomZipOutputStreamUtil.addSimpleEntry(stream, StroomZipFile.SINGLE_DATA_ENTRY,
                    "dummy".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        // Roll this REPO
        proxyRepositoryManager.doRunWork();

        assertThat(proxyRepositoryManager.getReadableRepository().size()).isEqualTo(2);
    }

    @Test
    void testNonRolling() throws IOException {
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

        assertThat(proxyRepositoryManager.getReadableRepository().size()).isEqualTo(1);

    }
}
