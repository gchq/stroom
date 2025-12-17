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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.exception.AuthenticationException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;

public class StroomStreamException extends RuntimeException {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomStreamException.class);

    private final StroomStreamStatus stroomStreamStatus;

    public StroomStreamException(final StroomStatusCode stroomStatusCode,
                                 final AttributeMap attributeMap) {
        this(new StroomStreamStatus(stroomStatusCode, attributeMap));
    }

    public StroomStreamException(final StroomStatusCode stroomStatusCode,
                                 final AttributeMap attributeMap,
                                 final String message) {
        this(new StroomStreamStatus(stroomStatusCode, attributeMap), message);
    }

    public StroomStreamException(final StroomStreamStatus stroomStreamStatus) {
        super(stroomStreamStatus.buildStatusMessage());
        this.stroomStreamStatus = stroomStreamStatus;
    }

    public StroomStreamException(final StroomStreamStatus stroomStreamStatus,
                                 final String message) {
        super(stroomStreamStatus.buildStatusMessage(message));
        this.stroomStreamStatus = stroomStreamStatus;
    }

    public static StroomStreamException create(final Throwable ex, final AttributeMap attributeMap) {
        final RuntimeException unwrappedException = unwrap(ex, attributeMap);
        if (unwrappedException instanceof final StroomStreamException stroomStreamException) {
            return stroomStreamException;
        } else {
            return new StroomStreamException(
                    StroomStatusCode.UNKNOWN_ERROR,
                    attributeMap,
                    unwrappedException.getMessage());
        }
    }

    private static RuntimeException unwrap(final Throwable ex, final AttributeMap attributeMap) {
        if (ex instanceof ZipException) {
            return new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, attributeMap, ex.getMessage());
        } else if (ex instanceof DataFormatException) {
            return new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, attributeMap, ex.getMessage());
        } else if (ex instanceof AuthenticationException) {
            return new StroomStreamException(
                    StroomStatusCode.CLIENT_TOKEN_OR_CERT_NOT_AUTHENTICATED, attributeMap, ex.getMessage());
        } else if (ex instanceof ContentTooLargeException) {
            return new StroomStreamException(
                    StroomStatusCode.CONTENT_TOO_LARGE, attributeMap, ex.getMessage());
        } else if (ex instanceof final StroomStreamException stroomStreamException) {
            return stroomStreamException;
        } else if (ex.getCause() != null) {
            return unwrap(ex.getCause(), attributeMap);
        } else if (ex instanceof final RuntimeException runtimeException) {
            return runtimeException;
        } else {
            return new RuntimeException(ex);
        }
    }

    public void sendErrorResponse(final HttpServletResponse httpServletResponse) {
        LOGGER.debug(getMessage(), this);
        LOGGER.warn(() -> "Sending error response " + stroomStreamStatus + " " + getMessage());

        final StroomStatusCode stroomStatusCode = stroomStreamStatus.getStroomStatusCode();
        httpServletResponse.setStatus(stroomStatusCode.getHttpCode());
        httpServletResponse.setHeader(
                StandardHeaderArguments.STROOM_STATUS,
                String.valueOf(stroomStatusCode.getCode()));
        // It would make more sense to return the error msg as a plain text body, but that would
        // mean a change to the API which we can't risk
        httpServletResponse.setHeader(
                StandardHeaderArguments.STROOM_ERROR,
                String.valueOf(getMessage()));

        try {
            httpServletResponse.sendError(stroomStatusCode.getHttpCode(), getMessage());
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public StroomStreamStatus getStroomStreamStatus() {
        return stroomStreamStatus;
    }

    public AttributeMap getAttributeMap() {
        return NullSafe.getOrElseGet(
                stroomStreamStatus,
                StroomStreamStatus::getAttributeMap,
                AttributeMap::new);
    }

    public StroomStatusCode getStroomStatusCode() {
        return NullSafe.getOrElse(
                stroomStreamStatus,
                StroomStreamStatus::getStroomStatusCode,
                StroomStatusCode.UNKNOWN_ERROR);
    }
}
