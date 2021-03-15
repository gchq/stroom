package stroom.proxy.repo;

import stroom.data.zip.StroomZipOutputStream;
import stroom.meta.api.AttributeMap;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.writer.AbstractDestinationProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import javax.inject.Inject;

//@Scope(StroomScope.PROTOTYPE)
//@ConfigurableElement(
//        type = "ProxyRepositoryStreamAppender",
//        category = Category.DESTINATION,
//        roles = {PipelineElementType.ROLE_TARGET,
//                PipelineElementType.ROLE_DESTINATION,
//                PipelineElementType.VISABILITY_STEPPING},
//        icon = ElementIcons.STREAM)
public class ProxyRepositoryStreamAppender extends AbstractDestinationProvider implements Destination {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryStreamAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final MetaDataHolder metaDataHolder;
    private final ProxyRepo proxyRepo;

    private StroomZipOutputStream stroomZipOutputStream;
    private OutputStream outputStream;
    private boolean doneOne = false;
    private long count;

    @Inject
    ProxyRepositoryStreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                                  final MetaDataHolder metaDataHolder,
                                  final ProxyRepo proxyRepo) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.metaDataHolder = metaDataHolder;
        this.proxyRepo = proxyRepo;
    }

    @Override
    public void endProcessing() {
        try {
            if (stroomZipOutputStream != null) {
                if (doneOne) {
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
            final AttributeMap attributeMap = metaDataHolder.getMetaData();
            stroomZipOutputStream = proxyRepo.getStroomZipOutputStream(attributeMap);
            nextEntry();
        }

        return outputStream;
    }


    private void nextEntry() throws IOException {
        if (stroomZipOutputStream != null) {
            count++;
            final AttributeMap attributeMap = metaDataHolder.getMetaData();
            String fileName = attributeMap.get("fileName");
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
