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

package stroom.pipeline.cache;

import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.xsltfunctions.StroomXsltFunctionLibrary;

import net.sf.saxon.s9api.XsltExecutable;

public class StoredXsltExecutable {

    private final XsltExecutable xsltExecutable;
    private final StroomXsltFunctionLibrary functionLibrary;
    private final StoredErrorReceiver errorReceiver;

    public StoredXsltExecutable(final XsltExecutable xsltExecutable, final StroomXsltFunctionLibrary functionLibrary,
                                final StoredErrorReceiver errorReceiver) {
        this.xsltExecutable = xsltExecutable;
        this.functionLibrary = functionLibrary;
        this.errorReceiver = errorReceiver;
    }

    public XsltExecutable getXsltExecutable() {
        return xsltExecutable;
    }

    public StroomXsltFunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    public StoredErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }
}
