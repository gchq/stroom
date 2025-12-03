/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.parser;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.factory.HasTargets;
import stroom.pipeline.factory.Processor;
import stroom.pipeline.factory.TakesInput;
import stroom.pipeline.factory.TakesReader;
import stroom.pipeline.factory.Target;
import stroom.pipeline.filter.ExitSteppingException;
import stroom.pipeline.filter.NullXMLFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.filter.XMLFilterForkFactory;
import stroom.pipeline.reader.ByteStreamDecoder.DecoderException;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import net.sf.saxon.trans.XPathException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.TransformerException;

public abstract class AbstractParser extends AbstractElement implements TakesInput, TakesReader, Target, HasTargets {

    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;

    private InputSource inputSource;
    private XMLFilter filter = NullXMLFilter.INSTANCE;

    private XMLReader xmlReader;

    public AbstractParser(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
    }

    @Override
    public void setInputStream(final InputStream inputStream, final String encoding) {
        inputSource = new InputSource(inputStream);
        inputSource.setEncoding(encoding);
    }

    @Override
    public void setReader(final Reader reader) {
        inputSource = new InputSource(reader);
    }

    @Override
    public List<Processor> createProcessors() {
        final Processor processor = () -> parse(inputSource);
        final List<Processor> childProcessors = filter.createProcessors();

        final List<Processor> processors = new ArrayList<>(childProcessors.size() + 1);
        processors.add(processor);
        processors.addAll(childProcessors);

        return processors;
    }

    @Override
    public void addTarget(final Target target) {
        this.filter = XMLFilterForkFactory.addTarget(getElementId(), this.filter, target);
    }

    @Override
    public void setTarget(final Target target) {
        this.filter = XMLFilterForkFactory.setTarget(getElementId(), target);
    }

    public XMLFilter getFilter() {
        return filter;
    }

    @Override
    public void startProcessing() {
        try {
            xmlReader = createReader();
            if (xmlReader != null) {
                xmlReader.setContentHandler(getFilter());

                final ErrorHandler errorHandler = new ErrorHandlerAdaptor(getElementId(), locationFactory,
                        errorReceiverProxy);
                xmlReader.setErrorHandler(errorHandler);

                // Errors may be throw right away as start processing
                // compiles XSLT etc. If an error is thrown we don't
                // want to keep on parsing.
                filter.startProcessing();
            }
        } catch (final LoggedException e) {
            throw e;
        } catch (final SAXException | RuntimeException e) {
            fatal(e);
        }
    }

    @Override
    public void endProcessing() {
        try {
            filter.endProcessing();
        } catch (final LoggedException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw ProcessException.wrap(e);
        } finally {
            // Make sure the error is added to the error count.
            final ErrorReceiver receiver = errorReceiverProxy.getErrorReceiver();
            if (receiver instanceof final ErrorStatistics errorStatistics) {
                errorStatistics.checkRecord(-1);
            }
        }
    }

    @Override
    public void startStream() {
        filter.startStream();
    }

    @Override
    public void endStream() {
        filter.endStream();
    }

    protected void parse(final InputSource inputSource) {
        if (xmlReader != null && getFilter() != null) {
            try {
                final InputSource internalInputSource = getInputSource(inputSource);

                /*
                 * THE FOLLOWING CODE IS HERE FOR DEBUGGING PURPOSES - DO NOT
                 * REMOVE!
                 */
                // final char[] buf = new char[10000];
                // final int len = inputSource.getCharacterStream().read(buf);
                // final String data = new String(buf, 0, len);
                // System.out.println(data);

                // try {
                // // Setup filters before parsing.
                // filter.startStream();

                // Parse the data.
                /*
                 * THE FOLLOWING CODE IS HERE FOR DEBUGGING PURPOSES - DO NOT
                 * REMOVE!
                 */
                // xmlReader.parse(new InputSource(new StringReader(data)));

                xmlReader.parse(internalInputSource);

            } catch (final ExitSteppingException e) {
                // This is expected so do nothing.

            } catch (final TaskTerminatedException | LoggedException e) {
                throw e;
            } catch (final ClosedByInterruptException e) {
                throw new TaskTerminatedException();
            } catch (final IOException | SAXException e) {
                final ProcessException processException = ProcessException.wrap(e);
                fatal(e);
                throw processException;
            } catch (final RuntimeException e) {
                Throwable exception = e;
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof ExitSteppingException) {
                        exception = null;
                    } else if (cause instanceof final TaskTerminatedException taskTerminatedException) {
                        throw taskTerminatedException;
                    } else if (cause instanceof final LoggedException loggedException) {
                        throw loggedException;
                    } else if (cause instanceof SAXException) {
                        exception = cause;
                        break;
                    } else if (cause instanceof final XPathException xPathException) {
                        cause = xPathException.getException();
                    } else if (cause instanceof TransformerException) {
                        exception = cause;
                        break;
                    } else if (cause instanceof DecoderException) {
                        exception = cause;
                        break;
                    } else if (cause instanceof final ProcessException processException) {
                        cause = processException.getXPathException();
                    } else {
                        cause = cause.getCause();
                    }
                }

                final ProcessException processException = ProcessException.wrap(exception);
                fatal(exception);

                throw processException;
            }
        }
    }

    protected abstract XMLReader createReader() throws SAXException;

    /**
     * Provide a chance for parser implementations to modify or wrap the input
     * source.
     */
    protected InputSource getInputSource(final InputSource inputSource) throws IOException {
        // Set the character encoding to use.
        String charsetName = StreamUtil.DEFAULT_CHARSET_NAME;
        if (inputSource.getEncoding() != null && inputSource.getEncoding().trim().length() > 0) {
            charsetName = inputSource.getEncoding();
        }

        final InputSource internalInputSource;

        // If the input source is not raw bytes then assume that the user has
        // added the appropriate readers to the byte
        // stream and provided a character reader.
        if (inputSource.getCharacterStream() != null) {
            if (inputSource.getCharacterStream() instanceof BufferedReader) {
                internalInputSource = new InputSource(inputSource.getCharacterStream());
            } else {
                internalInputSource = new InputSource(new BufferedReader(inputSource.getCharacterStream()));
            }
        } else {
            final Reader inputStreamReader = new InputStreamReader(inputSource.getByteStream(), charsetName);
            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            internalInputSource = new InputSource(bufferedReader);
        }

        internalInputSource.setEncoding(charsetName);
        return internalInputSource;
    }

    protected void info(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.INFO, getLocation(), getElementId(), message, t);
    }

    protected void warning(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.WARNING, getLocation(), getElementId(), message, t);
    }

    protected void error(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.ERROR, getLocation(), getElementId(), message, t);
    }

    protected void fatal(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.FATAL_ERROR, getLocation(), getElementId(), message, t);
    }

    protected void info(final Throwable t) {
        info(t.getMessage(), t);
    }

    protected void warning(final Throwable t) {
        warning(t.getMessage(), t);
    }

    protected void error(final Throwable t) {
        error(t.getMessage(), t);
    }

    protected void fatal(final Throwable t) {
        fatal(t.getMessage(), t);
    }

    private Location getLocation() {
        return locationFactory == null
                ? null
                : locationFactory.create();
    }

    public ErrorReceiverProxy getErrorReceiverProxy() {
        return errorReceiverProxy;
    }
}
