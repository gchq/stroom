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

package stroom.pipeline.xml.converter.ds3;

import org.xml.sax.Attributes;

public class DataAttributes implements Attributes {
    private static final String BLANK = "";
    private static final String STRING = "string";
    private static final String[] NAME_VALUE = {"name", "value"};
    private static final String[] NAME_ONLY = {"name"};
    private static final String[] VALUE_ONLY = {"value"};
    private static final String[] NONE = new String[0];
    private Type type;
    private String[] names;
    private String[] atts;

    public void setData(final String name, final String value) {
        if (name != null && value != null) {
            type = Type.NAME_VALUE;
            names = NAME_VALUE;
            atts = new String[2];
            atts[0] = name;
            atts[1] = value;
        } else if (name != null) {
            type = Type.NAME_ONLY;
            names = NAME_ONLY;
            atts = new String[1];
            atts[0] = name;
        } else if (value != null) {
            type = Type.VALUE_ONLY;
            names = VALUE_ONLY;
            atts = new String[1];
            atts[0] = value;
        } else {
            type = Type.NONE;
            names = NONE;
            atts = null;
        }
    }

    @Override
    public int getIndex(final String uri, final String localName) {
        if (localName.equals("name")) {
            if (type == Type.NAME_VALUE || type == Type.NAME_ONLY) {
                return 0;
            }
        } else if (localName.equals("value")) {
            if (type == Type.NAME_VALUE) {
                return 1;
            } else if (type == Type.VALUE_ONLY) {
                return 0;
            }
        }

        return -1;
    }

    @Override
    public int getIndex(final String qName) {
        return getIndex(null, qName);
    }

    @Override
    public int getLength() {
        return names.length;
    }

    @Override
    public String getLocalName(final int index) {
        return names[index];
    }

    @Override
    public String getQName(final int index) {
        return getLocalName(index);
    }

    @Override
    public String getType(final int index) {
        return STRING;
    }

    @Override
    public String getType(final String uri, final String localName) {
        return STRING;
    }

    @Override
    public String getType(final String qName) {
        return STRING;
    }

    @Override
    public String getURI(final int index) {
        return BLANK;
    }

    @Override
    public String getValue(final int index) {
        return atts[index];
    }

    @Override
    public String getValue(final String uri, final String localName) {
        return getValue(getIndex(localName));
    }

    @Override
    public String getValue(final String qName) {
        return getValue(getIndex(qName));
    }

    private enum Type {
        NAME_VALUE, NAME_ONLY, VALUE_ONLY, NONE
    }
}
