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

package stroom.pipeline.filter;

import stroom.util.io.StreamUtil;

import org.w3c.dom.ls.LSInput;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

public class LSInputImpl implements LSInput {

    private final String data;
    private final String systemId;
    private final String publicId;
    private final String baseURI;

    public LSInputImpl(final String data, final String systemId, final String publicId, final String baseURI) {
        this.data = data;
        this.systemId = systemId;
        this.publicId = publicId;
        this.baseURI = baseURI;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void setSystemId(final String systemId) {
    }

    @Override
    public String getStringData() {
        return data;
    }

    @Override
    public void setStringData(final String stringData) {
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public void setPublicId(final String publicId) {
    }

    @Override
    public String getEncoding() {
        return "UTF-8";
    }

    @Override
    public void setEncoding(final String encoding) {
    }

    @Override
    public Reader getCharacterStream() {
        return new StringReader(data);
    }

    @Override
    public void setCharacterStream(final Reader characterStream) {
    }

    @Override
    public boolean getCertifiedText() {
        return false;
    }

    @Override
    public void setCertifiedText(final boolean certifiedText) {
    }

    @Override
    public InputStream getByteStream() {
        return StreamUtil.stringToStream(data);
    }

    @Override
    public void setByteStream(final InputStream byteStream) {
    }

    @Override
    public String getBaseURI() {
        return baseURI;
    }

    @Override
    public void setBaseURI(final String baseURI) {
    }
}
