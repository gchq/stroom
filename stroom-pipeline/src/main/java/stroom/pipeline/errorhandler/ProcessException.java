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

package stroom.pipeline.errorhandler;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import java.util.Objects;

/**
 * Exception used to wrap all exceptions generated within transformation code.
 */
public class ProcessException extends UncheckedXPathException {

    ProcessException(final XPathException cause) {
        super(cause);
    }

    public static ProcessException create(final String message) {
        return new ProcessException(new XPathException(message));
    }

    public static ProcessException create(final String message, final Throwable throwable) {
        return new ProcessException(new XPathException(message, throwable));
    }

    public static ProcessException wrap(final Throwable throwable) {
        if (throwable instanceof ProcessException) {
            return (ProcessException) throwable;
        }
        if (throwable instanceof XPathException) {
            return new ProcessException((XPathException) throwable);
        }
        if (throwable instanceof UncheckedXPathException) {
            return new ProcessException(((UncheckedXPathException) throwable).getXPathException());
        }
        return new ProcessException(new XPathException(throwable));
    }

    public static ProcessException wrap(final String message, final Throwable throwable) {
        if (Objects.equals(message, throwable.getMessage())) {
            return wrap(throwable);
        }
        return new ProcessException(new XPathException(message, throwable));
    }
}
