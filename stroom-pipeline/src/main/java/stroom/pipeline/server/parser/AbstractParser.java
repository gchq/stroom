/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ErrorStatistics;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.pipeline.server.factory.AbstractElement;
import stroom.pipeline.server.factory.HasTargets;
import stroom.pipeline.server.factory.Processor;
import stroom.pipeline.server.factory.TakesInput;
import stroom.pipeline.server.factory.TakesReader;
import stroom.pipeline.server.factory.Target;
import stroom.pipeline.server.filter.ExitSteppingException;
import stroom.pipeline.server.filter.NullXMLFilter;
import stroom.pipeline.server.filter.XMLFilter;
import stroom.pipeline.server.filter.XMLFilterForkFactory;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

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
            xmlReader.setContentHandler(getFilter());

            final ErrorHandler errorHandler = new ErrorHandlerAdaptor(getElementId(), locationFactory,
                    errorReceiverProxy);
            xmlReader.setErrorHandler(errorHandler);

            // Errors may be throw right away as start processing
            // compiles XSLT etc. If an error is thrown we don't
            // want to keep on parsing.
            filter.startProcessing();

        } catch (final LoggedException e) {
            throw e;
        } catch (final Throwable e) {
            fatal(e);
        }
    }

    @Override
    public void endProcessing() {
        try {
            filter.endProcessing();
        } catch (final LoggedException e) {
            throw e;
        } catch (final Throwable e) {
            throw ProcessException.wrap(e);
        } finally {
            // Make sure the error is added to the error count.
            final ErrorReceiver receiver = errorReceiverProxy.getErrorReceiver();
            if (receiver instanceof ErrorStatistics) {
                final ErrorStatistics errorStatistics = (ErrorStatistics) receiver;
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

            } catch (final TerminatedException e) {
                throw e;
            } catch (final LoggedException e) {
                throw e;
            } catch (final Throwable e) {
                Throwable exception = e;
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof ExitSteppingException) {
                        exception = null;
                    } else if (cause instanceof TerminatedException) {
                        throw (TerminatedException) cause;
                    } else if (cause instanceof LoggedException) {
                        throw (LoggedException) cause;
                    } else if (cause instanceof SAXException) {
                        exception = cause;
                        break;
                    } else {
                        cause = cause.getCause();
                    }
                }

                if (exception != null) {
                    final ProcessException processException = ProcessException.wrap(exception);
                    fatal(exception);

                    throw processException;
                }
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

        InputSource internalInputSource = inputSource;

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
        errorReceiverProxy.log(Severity.INFO, null, getElementId(), message, t);
    }

    protected void warning(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), message, t);
    }

    protected void error(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, t);
    }

    protected void fatal(final String message, final Throwable t) {
        errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), message, t);
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

    public ErrorReceiverProxy getErrorReceiverProxy() {
        return errorReceiverProxy;
    }
}
