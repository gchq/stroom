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

package stroom.pipeline.xml.converter;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.io.IOException;

/**
 * Acts as an adapter for the XMLReader interface so that subclasses do not need
 * to implement all methods.
 */
public abstract class AbstractParser implements XMLReader {
    private static final String NOT_IMPLEMENTED = "Not implemented.";

    private ContentHandler contentHandler;
    private ErrorHandler errorHandler;

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        // Not implemented.
    }

    @Override
    public void parse(final String systemId) throws IOException, SAXException {
        throw new SAXNotSupportedException(NOT_IMPLEMENTED);
    }

    @Override
    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    @Override
    public void setContentHandler(final ContentHandler handler) {
        contentHandler = handler;
    }

    @Override
    public DTDHandler getDTDHandler() {
        // Not implemented.
        return null;
    }

    @Override
    public void setDTDHandler(final DTDHandler handler) {
        // Not implemented.
    }

    @Override
    public EntityResolver getEntityResolver() {
        // Not implemented.
        return null;
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        // Not implemented.
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public void setErrorHandler(final ErrorHandler handler) {
        errorHandler = handler;
    }

    @Override
    public void setFeature(final String name, final boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        // Not implemented.
    }

    @Override
    public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        // Not implemented.
        return true;
    }

    @Override
    public void setProperty(final String name, final Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        // Not implemented.
    }

    @Override
    public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        // Not implemented.
        return null;
    }
}
