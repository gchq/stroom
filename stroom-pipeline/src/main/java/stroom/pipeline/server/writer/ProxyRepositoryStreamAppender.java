package stroom.pipeline.server.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.MetaMap;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.StroomZipOutputStream;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "ProxyRepositoryStreamAppender",
        category = Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STREAM)
public class ProxyRepositoryStreamAppender extends AbstractAppender {
    private static Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryStreamAppender.class);

    private final MetaDataHolder metaDataHolder;
    private final ProxyRepositoryManager proxyRepositoryManager;

    private StroomZipOutputStream stroomZipOutputStream;
    private OutputStream entryStream;
    private boolean doneOne = false;
    private long count;

    @Inject
    ProxyRepositoryStreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                                  final MetaDataHolder metaDataHolder,
                                  final ProxyRepositoryManager proxyRepositoryManager) {
        super(errorReceiverProxy);
        this.metaDataHolder = metaDataHolder;
        this.proxyRepositoryManager = proxyRepositoryManager;
    }

    @Override
    public Destination borrowDestination() throws IOException {
        nextEntry();
        return super.borrowDestination();
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        closeEntry();
        super.returnDestination(destination);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        super.getOutputStream(header, footer);
        return entryStream;
    }

    @Override
    protected OutputStream createOutputStream() throws IOException {
        final MetaMap metaMap = metaDataHolder.getMetaData();
        stroomZipOutputStream = proxyRepositoryManager.getActiveRepository().getStroomZipOutputStream(metaMap);
        nextEntry();
        return entryStream;
    }

    private void nextEntry() throws IOException {
        if (stroomZipOutputStream != null) {
            count++;
            final MetaMap metaMap = metaDataHolder.getMetaData();
            String fileName = metaMap.get("fileName");
            if (fileName == null) {
                fileName = ModelStringUtil.zeroPad(3, String.valueOf(count)) + ".dat";
            }

            doneOne = true;
            entryStream = stroomZipOutputStream.addEntry(fileName);
        }
    }

    private void closeEntry() throws IOException {
        if (entryStream != null) {
            entryStream.close();
            entryStream = null;
        }
    }


//    @Override
//    public void setMetaMap(final MetaMap metaMap) {
//        this.metaMap = metaMap;
//    }
//
//    @Override
//    public void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
//        doneOne = true;
//        entryStream = stroomZipOutputStream.addEntry(stroomZipEntry.getFullName());
//    }
//
//    @Override
//    public void handleEntryEnd() throws IOException {
//        entryStream.close();
//        entryStream = null;
//    }
//
//    @Override
//    public void handleEntryData(byte[] buffer, int off, int len) throws IOException {
//        entryStream.write(buffer, off, len);
//    }


    @Override
    public void endProcessing() {
        try {
            if (stroomZipOutputStream != null) {
                if (doneOne) {
                    final MetaMap metaMap = metaDataHolder.getMetaData();
                    stroomZipOutputStream.addMissingMetaMap(metaMap);
                    stroomZipOutputStream.close();
                } else {
                    stroomZipOutputStream.closeDelete();
                }
            }
            stroomZipOutputStream = null;
            entryStream = null;
        } catch (final IOException e) {
            error(e.getMessage(), e);
        } finally {
            super.endProcessing();
        }
    }

//    @Override
//    public void handleFooter() throws IOException {
//
//    }

//    @Override
//    public void handleHeader() throws IOException {
//        stroomZipOutputStream = proxyRepositoryManager.getActiveRepository().getStroomZipOutputStream(metaMap);
//    }

    @Override
    protected void error(final String message, final Exception e) {
        super.error(message, e);
        try {
            if (stroomZipOutputStream != null) {
                LOGGER.info("Removing part written file {}", stroomZipOutputStream);
                stroomZipOutputStream.closeDelete();
                stroomZipOutputStream = null;
                entryStream = null;
            }
        } catch (final IOException ioe) {
            super.error(message, ioe);
        }
    }
}