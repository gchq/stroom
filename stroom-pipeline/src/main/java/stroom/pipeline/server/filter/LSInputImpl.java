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

package stroom.pipeline.server.filter;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.w3c.dom.ls.LSInput;

import stroom.util.io.StreamUtil;

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
    public void setSystemId(String systemId) {
    }

    @Override
    public void setStringData(String stringData) {
    }

    @Override
    public void setPublicId(String publicId) {
    }

    @Override
    public void setEncoding(String encoding) {
    }

    @Override
    public void setCharacterStream(Reader characterStream) {
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {
    }

    @Override
    public void setByteStream(InputStream byteStream) {
    }

    @Override
    public void setBaseURI(String baseURI) {
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public String getStringData() {
        return data;
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public String getEncoding() {
        return "UTF-8";
    }

    @Override
    public Reader getCharacterStream() {
        return new StringReader(data);
    }

    @Override
    public boolean getCertifiedText() {
        return false;
    }

    @Override
    public InputStream getByteStream() {
        return StreamUtil.stringToStream(data);
    }

    @Override
    public String getBaseURI() {
        return baseURI;
    }
}
