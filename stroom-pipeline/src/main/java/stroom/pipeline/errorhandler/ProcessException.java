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

package stroom.pipeline.server.errorhandler;

/**
 * Exception used to wrap all exceptions generated within transformation code.
 */
public class ProcessException extends RuntimeException {
    private static final long serialVersionUID = 6230610711953733732L;

    private String message;

    /**
     * Wraps Exception constructor.
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link #getMessage()} method.
     */
    public ProcessException(final String message) {
        this(message, null);
    }

    /**
     * Wraps Exception constructor.
     *
     * @param message the detail message (which is saved for later retrieval by the
     *                {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). (A <tt>null</tt> value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     */
    public ProcessException(final String message, final Throwable cause) {
        super(message, cause);
        this.message = MessageUtil.getMessage(message, cause);
    }

    public static final ProcessException wrap(final Throwable throwable) {
        if (throwable instanceof ProcessException) {
            return (ProcessException) throwable;
        }
        return new ProcessException(throwable.getMessage(), throwable);
    }

    public static final ProcessException wrap(final String msg, final Throwable throwable) {
        if (throwable instanceof ProcessException) {
            return (ProcessException) throwable;
        }
        return new ProcessException(msg, throwable);
    }

    @Override
    public String getMessage() {
        return message;
    }
}
