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
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StroomStreamException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomStreamException.class);

    private final StroomStatusCode stroomStatusCode;
    private final AttributeMap attributeMap;

    public StroomStreamException(final StroomStatusCode stroomStatusCode,
                                 final HttpServletRequest httpServletRequest,
                                 final Object... args) {
        this(stroomStatusCode, extractAttributeMap(httpServletRequest), args);
    }

    public StroomStreamException(final StroomStatusCode stroomStatusCode,
                                 final AttributeMap attributeMap,
                                 final Object... args) {
        super(buildStatusMessage(stroomStatusCode, attributeMap, args));
        this.stroomStatusCode = stroomStatusCode;
        this.attributeMap = attributeMap;
    }

    public static String buildStatusMessage(final StroomStatusCode stroomStatusCode,
                                            final AttributeMap attributeMap,
                                            final Object... args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Stroom Status ");
        if (stroomStatusCode != null) {
            builder.append(stroomStatusCode.getCode())
                    .append(" - ")
                    .append(stroomStatusCode.getMessage());
        } else {
            builder.append("null");
        }

        AttributeMapUtil.appendAttributes(
                attributeMap,
                builder,
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.COMPRESSION,
                StandardHeaderArguments.TYPE);

        if (args != null) {
            for (final Object object : args) {
                builder.append(" - ")
                        .append(object);
            }
        }
        return builder.toString();
    }

    public static void createAndThrow(final Throwable ex, final AttributeMap attributeMap) {
        RuntimeException unwrappedException = unwrap(ex, attributeMap);
        if (unwrappedException instanceof StroomStreamException) {
            throw unwrappedException;
        } else {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR,
                    attributeMap,
                    unwrappedException.getMessage());
        }
    }


    private static RuntimeException unwrap(final Throwable ex, final AttributeMap attributeMap) {
        if (ex instanceof ZipException) {
            throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, attributeMap, ex.getMessage());
        } else if (ex instanceof DataFormatException) {
            throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, attributeMap, ex.getMessage());
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

    public static RuntimeException sendErrorResponse(final HttpServletRequest httpServletRequest,
                                                     final HttpServletResponse httpServletResponse,
                                                     final Exception exception) {
        final AttributeMap attributeMap = extractAttributeMap(httpServletRequest);

        final RuntimeException unwrappedException = unwrap(exception, attributeMap);

        StroomStatusCode stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        final String message = unwrapMessage(unwrappedException);

        if (unwrappedException instanceof StroomStreamException) {
            stroomStatusCode = ((StroomStreamException) unwrappedException).getStroomStatusCode();
        }

        if (stroomStatusCode == null) {
            stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        }


        final StroomStatusCode finalStroomStatusCode = stroomStatusCode;
        LOGGER.warn(() -> {
            final StringBuilder clientDetailsStringBuilder = new StringBuilder();
            AttributeMapUtil.appendAttributes(
                    attributeMap,
                    clientDetailsStringBuilder,
                    StandardHeaderArguments.X_FORWARDED_FOR,
                    StandardHeaderArguments.REMOTE_HOST,
                    StandardHeaderArguments.REMOTE_ADDRESS,
                    StandardHeaderArguments.RECEIVED_PATH);

            final String clientDetailsStr = clientDetailsStringBuilder.isEmpty()
                    ? ""
                    : " - " + clientDetailsStringBuilder;

            return "Sending error response "
                    + finalStroomStatusCode.getHttpCode()
                    + " - "
                    + message
                    + clientDetailsStr;
        });

        httpServletResponse.setHeader(StandardHeaderArguments.STROOM_STATUS,
                String.valueOf(stroomStatusCode.getCode()));

        try {
            httpServletResponse.sendError(stroomStatusCode.getHttpCode(), message);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return unwrappedException;

    }

    private static AttributeMap extractAttributeMap(final HttpServletRequest httpServletRequest) {
        AttributeMap attributeMap = null;
        try {
            attributeMap = AttributeMapUtil.create(httpServletRequest);
        } catch (Exception e) {
            LOGGER.error("Unable to extract attribute map from request", e);
        }
        return attributeMap;
    }

    protected static String unwrapMessage(final Throwable throwable) {
        final StringBuilder stringBuilder = new StringBuilder();
        unwrapMessage(stringBuilder, throwable, 10);
        return stringBuilder.toString();
    }

    protected static void unwrapMessage(final StringBuilder stringBuilder,
                                        final Throwable throwable,
                                        final int depth) {
        if (depth == 0 || throwable == null) {
            return;
        }
        stringBuilder.append(throwable.getMessage());

        final Throwable cause = throwable.getCause();
        if (cause != null) {
            stringBuilder.append(" - ");
            unwrapMessage(stringBuilder, throwable.getCause(), depth - 1);
        }
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }
}
