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

package stroom.pipeline.filter;

import org.xml.sax.Attributes;

public class WrappedAttributes implements Attributes {
    protected Attributes atts;

    public Attributes getAtts() {
        return atts;
    }

    public void setAtts(Attributes atts) {
        this.atts = atts;
    }

    @Override
    public int getIndex(String uri, String localName) {
        return atts.getIndex(uri, localName);
    }

    @Override
    public int getIndex(String qName) {
        return atts.getIndex(qName);
    }

    @Override
    public int getLength() {
        return atts.getLength();
    }

    @Override
    public String getLocalName(int index) {
        return atts.getLocalName(index);
    }

    @Override
    public String getQName(int index) {
        return atts.getQName(index);
    }

    @Override
    public String getType(int index) {
        return atts.getType(index);
    }

    @Override
    public String getType(String uri, String localName) {
        return atts.getType(uri, localName);
    }

    @Override
    public String getType(String qName) {
        return atts.getType(qName);
    }

    @Override
    public String getURI(int index) {
        return atts.getURI(index);
    }

    @Override
    public String getValue(int index) {
        return atts.getValue(index);
    }

    @Override
    public String getValue(String uri, String localName) {
        return atts.getValue(uri, localName);
    }

    @Override
    public String getValue(String qName) {
        return atts.getValue(qName);
    }
}
