package stroom.alert.impl;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.XMLFilter;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.ext.Attributes2Impl;
import org.xml.sax.helpers.AttributesImpl;

import javax.inject.Inject;

public class RecordWriter implements RecordConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordWriter.class);

    private static final Attributes2Impl BLANK_ATTRIBUTES = new Attributes2Impl();
    private static final String NAMESPACE_URI = "";
    private static final String XS_STRING = "xs:string";
    private static final String RECORDS = "records";
    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";


    private final ErrorReceiverProxy errorReceiverProxy;
    private XMLFilter handler;
    private boolean written;

    @Inject
    public RecordWriter(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    public void setHandler(final XMLFilter handler) {
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
    }

    @Override
    public synchronized void end() {
        try {
            if (written) {
                handler.endElement(NAMESPACE_URI, RECORDS, RECORDS);
                handler.endDocument();
                handler.endStream();
                handler.endProcessing();
            }
        } catch (final SAXException e) {
            log(Severity.ERROR, e.getMessage(), e);
        }
    }

    private void log(final Severity severity, final String message, final Exception e) {
        LOGGER.error(message, e);
        errorReceiverProxy.log(severity, null,
                getClass().getSimpleName(), message, e);
    }

    @Override
    public synchronized void accept(final Record record) {
        try {
            if (!written) {
                handler.startProcessing();
                handler.startStream();
                handler.startDocument();
                handler.startElement(NAMESPACE_URI, RECORDS, RECORDS, BLANK_ATTRIBUTES);
                written = true;
            }
            handler.startElement(NAMESPACE_URI, RECORD, RECORD, BLANK_ATTRIBUTES);

            for (final Data data : record.list()) {
                writeData(data.name(), data.value());
            }

            handler.endElement(NAMESPACE_URI, RECORD, RECORD);
        } catch (final SAXException e) {
            log(Severity.ERROR, e.getMessage(), e);
        }
    }

    private void writeData(String name, String value) {
        try {
            final AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(NAMESPACE_URI, NAME, NAME, XS_STRING, name);
            attrs.addAttribute(NAMESPACE_URI, VALUE, VALUE, XS_STRING, value);
            handler.startElement(NAMESPACE_URI, DATA, DATA, attrs);
            handler.endElement(NAMESPACE_URI, DATA, DATA);
        } catch (final SAXException e) {
            log(Severity.ERROR, e.getMessage(), e);
        }
    }
}
