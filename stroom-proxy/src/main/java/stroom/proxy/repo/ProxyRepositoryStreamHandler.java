package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.proxy.handler.StreamHandler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Factory to return back handlers for incoming and outgoing requests.
 */
public class ProxyRepositoryStreamHandler implements StreamHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryStreamHandler.class);

    private final ProxyRepositoryManager proxyRepositoryManager;

    private MetaMap metaMap;
    private StroomZipOutputStream stroomZipOutputStream;
    private OutputStream entryStream;
    private boolean doneOne = false;

    @Inject
    public ProxyRepositoryStreamHandler(final ProxyRepositoryManager proxyRepositoryManager) {
        this.proxyRepositoryManager = proxyRepositoryManager;
    }

    @Override
    public void setMetaMap(final MetaMap metaMap) {
        this.metaMap = metaMap;
    }

    @Override
    public void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
        doneOne = true;
        entryStream = stroomZipOutputStream.addEntry(stroomZipEntry.getFullName());
    }

    @Override
    public void handleEntryEnd() throws IOException {
        entryStream.close();
        entryStream = null;
    }

    @Override
    public void handleEntryData(byte[] buffer, int off, int len) throws IOException {
        entryStream.write(buffer, off, len);
    }

    @Override
    public void handleFooter() throws IOException {
        if (doneOne) {
            stroomZipOutputStream.addMissingMetaMap(metaMap);
            stroomZipOutputStream.close();
        } else {
            stroomZipOutputStream.closeDelete();
        }
        stroomZipOutputStream = null;
    }

    @Override
    public void handleHeader() throws IOException {
        stroomZipOutputStream = proxyRepositoryManager.getActiveRepository().getStroomZipOutputStream(metaMap);
    }

    @Override
    public void handleError() throws IOException {
        if (stroomZipOutputStream != null) {
            LOGGER.info("Removing part written file {}", stroomZipOutputStream);
            stroomZipOutputStream.closeDelete();
        }
    }

    @Override
    public void validate() {
    }
}