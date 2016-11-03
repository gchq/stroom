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

package stroom.xml.converter.json;

import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.XMLReader;

import stroom.xml.converter.ParserFactory;

@Component
@Scope(StroomScope.PROTOTYPE)
public class JSONParserFactory implements ParserFactory {
    private boolean addRootObject = true;

    @Override
    public XMLReader getParser() {
        return new JSONParser(addRootObject);
    }

    public void setAddRootObject(final boolean addRootObject) {
        this.addRootObject = addRootObject;
    }
}
