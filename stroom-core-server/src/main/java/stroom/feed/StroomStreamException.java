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

package stroom.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.StroomStatusCode;
import stroom.util.io.StreamUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;

public class StroomStreamException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStreamException.class);

    private final StroomStatusCode stroomStatusCode;

    public StroomStreamException(final StroomStatusCode stroomStatusCode, final Object... args) {
        super(buildMessage(stroomStatusCode, args));
        this.stroomStatusCode = stroomStatusCode;
    }

    private static String buildMessage(final StroomStatusCode stroomStatusCode, final Object[] args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Stroom Status ");
        if (stroomStatusCode != null) {
            builder.append(stroomStatusCode.getCode());
            builder.append(" - ");
            builder.append(stroomStatusCode.getMessage());
        } else {
            builder.append("null");
        }
        if (args != null) {
            for (final Object object : args) {
                builder.append(" - ");
                builder.append(object);
            }
        }
        return builder.toString();
    }

    public static void create(Throwable ex) {
        ex = unwrap(ex);
        if (ex instanceof StroomStreamException) {
            throw (StroomStreamException) ex;
        }
        throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ex.getMessage());

    }

    private static RuntimeException unwrap(final Throwable ex) {
        if (ex instanceof ZipException) {
            throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ex.getMessage());
        }
        if (ex instanceof DataFormatException) {
            throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ex.getMessage());
        }
        if (ex instanceof StroomStreamException) {
            return (StroomStreamException) ex;
        }
        if (ex.getCause() != null) {
            return unwrap(ex.getCause());
        }
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new RuntimeException(ex);
    }

    public static int checkConnectionResponse(final HttpURLConnection connection) {
        int responseCode;
        try {
            responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                final String message = getMessage(connection);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Connection response " + responseCode + ": " + message);
                }

                final int stroomStatus = connection.getHeaderFieldInt(StroomHeaderArguments.STROOM_STATUS, -1);
                if (stroomStatus != -1) {
                    throw new StroomStreamException(StroomStatusCode.getStroomStatusCode(stroomStatus), message);
                } else {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, message);
                }
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Connection response " + responseCode + ": " + getMessage(connection));
            }
        } catch (final IOException ioEx) {
            LOGGER.debug(ioEx.getMessage(), ioEx);
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ioEx.getMessage() != null ? ioEx.getMessage() : ioEx.toString());
        }
        return responseCode;
    }

    private static String getMessage(final HttpURLConnection connection) {
        String responseMessage = null;
        String inputMessage = null;
        String errorMessage = null;

        try {
            responseMessage = connection.getResponseMessage();
        } catch (final RuntimeException | IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        try {
            inputMessage = readAndCloseStream(connection.getInputStream());
        } catch (final RuntimeException | IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        try {
            errorMessage = readAndCloseStream(connection.getErrorStream());
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        final StringBuilder sb = new StringBuilder();
        if (responseMessage != null) {
            sb.append(responseMessage);
        }
        if (inputMessage != null) {
            sb.append(" ");
            sb.append(inputMessage);
        }
        if (errorMessage != null) {
            sb.append(" ");
            sb.append(errorMessage);
        }
        return sb.toString().trim();
    }

    private static String readAndCloseStream(final InputStream inputStream) {
        try {
            if (inputStream != null) {
                return StreamUtil.streamToString(inputStream);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    public static int sendErrorResponse(final HttpServletResponse httpServletResponse, Exception exception) {
        exception = unwrap(exception);

        StroomStatusCode stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        final String message = unwrapMessage(exception);

        if (exception instanceof StroomStreamException) {
            final StroomStreamException stroomStreamExcpetion = (StroomStreamException) exception;
            stroomStatusCode = stroomStreamExcpetion.getStroomStatusCode();
        }

        if (stroomStatusCode == null) {
            stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        }
        LOGGER.error("sendErrorResponse() - " + stroomStatusCode.getHttpCode() + " " + message);

        httpServletResponse.setHeader(StroomHeaderArguments.STROOM_STATUS, String.valueOf(stroomStatusCode.getCode()));

        try {
            httpServletResponse.sendError(stroomStatusCode.getHttpCode(), message);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return stroomStatusCode.getHttpCode();

    }

    protected static String unwrapMessage(final Throwable th) {
        final StringBuilder stringBuilder = new StringBuilder();
        unwrapMessage(stringBuilder, th, 10);
        return stringBuilder.toString();
    }

    protected static void unwrapMessage(final StringBuilder stringBuilder, final Throwable th, final int depth) {
        if (depth == 0 || th == null) {
            return;
        }
        stringBuilder.append(th.getMessage());
        stringBuilder.append(" - ");

        unwrapMessage(stringBuilder, th.getCause(), depth - 1);
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }
}