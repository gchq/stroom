package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.writer.AbstractDestinationProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

//@Scope(StroomScope.PROTOTYPE)
//@ConfigurableElement(
//        type = "ProxyRepositoryStreamAppender",
//        category = Category.DESTINATION,
//        roles = {PipelineElementType.ROLE_TARGET,
//                PipelineElementType.ROLE_DESTINATION,
//                PipelineElementType.VISABILITY_STEPPING},
//        icon = ElementIcons.STREAM)
public class ProxyRepositoryStreamAppender extends AbstractDestinationProvider implements Destination {
    private static Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryStreamAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final MetaDataHolder metaDataHolder;
    private final ProxyRepositoryManager proxyRepositoryManager;

    private StroomZipOutputStream stroomZipOutputStream;
    private OutputStream outputStream;
    private boolean doneOne = false;
    private long count;

    @Inject
    ProxyRepositoryStreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                                  final MetaDataHolder metaDataHolder,
                                  final ProxyRepositoryManager proxyRepositoryManager) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.metaDataHolder = metaDataHolder;
        this.proxyRepositoryManager = proxyRepositoryManager;
    }

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
            outputStream = null;
        } catch (final IOException e) {
            error(e.getMessage(), e);
        } finally {
            super.endProcessing();
        }

        super.endProcessing();
    }

    @Override
    public Destination borrowDestination() throws IOException {
        nextEntry();
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        closeEntry();
    }

    @Override
    public final OutputStream getByteArrayOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        if (outputStream == null) {
            final MetaMap metaMap = metaDataHolder.getMetaData();
            stroomZipOutputStream = proxyRepositoryManager.getActiveRepository().getStroomZipOutputStream(metaMap);
            nextEntry();
        }

        return outputStream;
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
            outputStream = stroomZipOutputStream.addEntry(fileName);
        }
    }

    private void closeEntry() throws IOException {
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
    }

    private void error(final String message, final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
        try {
            if (stroomZipOutputStream != null) {
                LOGGER.info("Removing part written file {}", stroomZipOutputStream);
                stroomZipOutputStream.closeDelete();
                stroomZipOutputStream = null;
                outputStream = null;
            }
        } catch (final IOException ioe) {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), ioe.getMessage(), ioe);
        }
    }
}