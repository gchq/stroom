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

package stroom.util.xml;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

public class FatalErrorListener implements ErrorListener {

    @Override
    public void warning(final TransformerException exception) throws TransformerException {
    }

    @Override
    public void error(final TransformerException exception) throws TransformerException {
        throw exception;
    }

    @Override
    public void fatalError(final TransformerException exception) throws TransformerException {
        throw exception;
    }
}
