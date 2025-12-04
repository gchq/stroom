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

package stroom.pipeline.errorhandler;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import java.util.Objects;

public class LoggedException extends ProcessException {

    private LoggedException(final XPathException xPathException) {
        super(xPathException);
    }

    public static LoggedException create(final String message) {
        return new LoggedException(new XPathException(message));
    }

    public static LoggedException wrap(final Throwable throwable) {
        if (throwable instanceof LoggedException) {
            return (LoggedException) throwable;
        }
        if (throwable instanceof XPathException) {
            return new LoggedException((XPathException) throwable);
        }
        if (throwable instanceof UncheckedXPathException) {
            return new LoggedException(((UncheckedXPathException) throwable).getXPathException());
        }
        return new LoggedException(new XPathException(throwable));
    }

    public static LoggedException wrap(final String message, final Throwable throwable) {
        if (Objects.equals(message, throwable.getMessage())) {
            return wrap(throwable);
        }
        return new LoggedException(new XPathException(message, throwable));
    }
}
