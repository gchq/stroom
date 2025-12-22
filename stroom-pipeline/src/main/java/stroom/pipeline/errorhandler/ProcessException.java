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

import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.shared.NullSafe;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.nio.channels.ClosedByInterruptException;
import java.util.Objects;
import javax.xml.transform.TransformerException;

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
        if (throwable != null) {
            if (Objects.equals(message, throwable.getMessage())) {
                return wrap(throwable);
            }
            return new ProcessException(new XPathException(message, throwable));
        } else {
            return new ProcessException(new XPathException(message));
        }
    }

    /**
     * @return True if e is an instance of or was caused by one of
     * {@link stroom.task.api.TaskTerminatedException}
     * {@link InterruptedException}
     * {@link ClosedByInterruptException}
     * {@link UncheckedInterruptedException}.
     * <p>
     * Similar (but more involved) functionality to {@link TaskTerminatedException#unwrap(Throwable)}
     */
    public static boolean isTerminated(final Throwable e) {
        if (e == null) {
            return false;
        } else if (e instanceof final TransformerException te) {
            return isTerminated(te.getException());
        } else if (e instanceof final ProcessException pe) {
            final Throwable wrappedThrowable = NullSafe.get(
                    pe.getXPathException(),
                    TransformerException::getException);
            return isTerminated(wrappedThrowable);
        } else if (contains(e, TaskTerminatedException.class)) {
            return true;
        } else if (contains(e, InterruptedException.class)) {
            return true;
        } else if (contains(e, UncheckedInterruptedException.class)) {
            return true;
        } else if (contains(e, ClosedByInterruptException.class)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return True if e is of type clazz or a throwable in its cause chain is of type clazz.
     */
    private static boolean contains(final Throwable e,
                                    final Class<? extends Throwable> clazz) {
        return e != null
               && clazz != null
               && (
                       clazz.isAssignableFrom(e.getClass())
                       || ExceptionUtils.indexOfThrowable(e, clazz) >= 0);
    }
}
