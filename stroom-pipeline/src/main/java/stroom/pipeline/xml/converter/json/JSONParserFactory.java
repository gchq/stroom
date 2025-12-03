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

package stroom.pipeline.xml.converter.json;

import stroom.pipeline.xml.converter.ParserFactory;

import org.xml.sax.XMLReader;

public class JSONParserFactory implements ParserFactory {

    private JSONFactoryConfig config = new JSONFactoryConfig();
    private boolean addRootObject = true;

    @Override
    public XMLReader getParser() {
        return new JSONParser(config, addRootObject);
    }

    public void setAddRootObject(final boolean addRootObject) {
        this.addRootObject = addRootObject;
    }

    public void setConfig(final JSONFactoryConfig config) {
        this.config = config;
    }
}
