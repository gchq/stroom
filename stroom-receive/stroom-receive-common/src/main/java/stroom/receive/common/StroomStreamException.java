/*
 * Copyright 2017 Crown Copyright
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;
import javax.servlet.http.HttpServletResponse;

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
        if (unwrappedException instanceof StroomStreamException) {
            return (StroomStreamException) unwrappedException;
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
        } else if (ex instanceof StroomStreamException) {
            return (StroomStreamException) ex;
        } else if (ex.getCause() != null) {
            return unwrap(ex.getCause(), attributeMap);
        } else if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        } else {
            return new RuntimeException(ex);
        }
    }

    public static int checkConnectionResponse(final HttpURLConnection connection, final AttributeMap attributeMap) {
        int responseCode = -1;
        int stroomStatus = -1;
        try {
            responseCode = connection.getResponseCode();
            final String responseMessage = connection.getResponseMessage();

            stroomStatus = connection.getHeaderFieldInt(StandardHeaderArguments.STROOM_STATUS, -1);

            if (responseCode == 200) {
                readAndCloseStream(connection.getInputStream());
            } else {
                readAndCloseStream(connection.getErrorStream());
            }

            if (responseCode != 200) {
                if (stroomStatus != -1) {
                    throw new StroomStreamException(
                            StroomStatusCode.getStroomStatusCode(stroomStatus),
                            attributeMap,
                            responseMessage);
                } else {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap, responseMessage);
                }
            }
        } catch (final IOException ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap, ioEx.getMessage());
        }
        return responseCode;
    }

    private static void readAndCloseStream(final InputStream inputStream) {
        final byte[] buffer = new byte[1024];
        try {
            if (inputStream != null) {
                while (inputStream.read(buffer) > 0) {
                }
                inputStream.close();
            }
        } catch (final IOException ioex) {
            // TODO @AT Should we be swallowing this
        }
    }

    public void sendErrorResponse(final HttpServletResponse httpServletResponse) {
        LOGGER.debug(getMessage(), this);
        LOGGER.warn(() -> "Sending error response " + stroomStreamStatus + " " + getMessage());

        final StroomStatusCode stroomStatusCode = stroomStreamStatus.getStroomStatusCode();
        httpServletResponse.setStatus(stroomStatusCode.getHttpCode());
        httpServletResponse.setHeader(StandardHeaderArguments.STROOM_STATUS,
                String.valueOf(stroomStatusCode.getCode()));

        try {
            httpServletResponse.sendError(stroomStatusCode.getHttpCode(), getMessage());
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public StroomStreamStatus getStroomStreamStatus() {
        return stroomStreamStatus;
    }
}
