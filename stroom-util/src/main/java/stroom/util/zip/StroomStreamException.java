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

package stroom.util.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        int responseCode = -1;
        int stroomStatus = -1;
        try {
            responseCode = connection.getResponseCode();
            final String responseMessage = connection.getResponseMessage();

            stroomStatus = connection.getHeaderFieldInt(StroomHeaderArguments.STROOM_STATUS, -1);

            if (responseCode == 200) {
                readAndCloseStream(connection.getInputStream());
            } else {
                readAndCloseStream(connection.getErrorStream());
            }

            if (responseCode != 200) {
                if (stroomStatus != -1) {
                    throw new StroomStreamException(StroomStatusCode.getStroomStatusCode(stroomStatus), responseMessage);
                } else {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, responseMessage);
                }
            }
        } catch (final IOException ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ioEx.getMessage());
        }
        return responseCode;
    }

    private static void readAndCloseStream(final InputStream inputStream) {
        byte[] BUFFER = new byte[1024];
        try {
            if (inputStream != null) {
                while (inputStream.read(BUFFER) > 0) {
                }
                inputStream.close();
            }
        } catch (final IOException ioex) {
        }
    }

    public static int sendErrorResponse(final HttpServletResponse httpServletResponse, Exception exception)
            throws IOException {
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
        httpServletResponse.sendError(stroomStatusCode.getHttpCode(), message);
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