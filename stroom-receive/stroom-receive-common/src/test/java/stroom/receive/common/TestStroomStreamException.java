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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStroomStreamException {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomStreamException.class);

    @Test
    void testCompressedStreamCorrupt() {
        doTest(new ZipException("test"), StroomStatusCode.COMPRESSED_STREAM_INVALID, "test");
        doTest(new RuntimeException(new ZipException("test")), StroomStatusCode.COMPRESSED_STREAM_INVALID, "test");
        doTest(new RuntimeException(new RuntimeException(new ZipException("test"))),
                StroomStatusCode.COMPRESSED_STREAM_INVALID, "test");
        doTest(new IOException(new ZipException("test")), StroomStatusCode.COMPRESSED_STREAM_INVALID, "test");
    }

    @Test
    void testOtherError() {
        doTest(new RuntimeException("test"), StroomStatusCode.UNKNOWN_ERROR, "test");
    }

    @Test
    void testAttributeValues() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, "MY_FEED");
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_GZIP);

        final StroomStatusCode stroomStatusCode = StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA;
        final String arg1 = "arg1";
        final StroomStreamException stroomStreamException = new StroomStreamException(
                stroomStatusCode, attributeMap, arg1);

        assertThatThrownBy(() -> {
            try {
                throw StroomStreamException.create(stroomStreamException, attributeMap);
            } catch (final Exception e) {
                LOGGER.info("msg: {}", e.getMessage());
                throw e;
            }
        })
                .hasMessageContaining("Stroom Status " + stroomStatusCode.getCode() + " - " +
                        stroomStatusCode.getMessage())
                .hasMessageContaining("arg1")
                .hasMessageContaining(StandardHeaderArguments.FEED + ": MY_FEED")
                .hasMessageContaining(StandardHeaderArguments.COMPRESSION
                        + ": "
                        + StandardHeaderArguments.COMPRESSION_GZIP);
    }

    @Test
    void testOneAttributeValue() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, "MY_FEED");

        final StroomStatusCode stroomStatusCode = StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA;
        final String arg1 = "arg1";
        final StroomStreamException stroomStreamException = new StroomStreamException(
                stroomStatusCode, attributeMap, arg1);

        assertThatThrownBy(() -> {
            try {
                throw StroomStreamException.create(stroomStreamException, attributeMap);
            } catch (final Exception e) {
                LOGGER.info("msg: {}", e.getMessage());
                throw e;
            }
        })
                .hasMessageContaining("Stroom Status " + stroomStatusCode.getCode() + " - " +
                        stroomStatusCode.getMessage())
                .hasMessageContaining("arg1")
                .hasMessageContaining(StandardHeaderArguments.FEED + ": MY_FEED");
    }

    @Test
    void testNestedExceptions() {

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, "MY_FEED");

        final StroomStatusCode stroomStatusCode = StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA;
        final String arg1 = "arg1";

        final Throwable throwable = new RuntimeException(
                "Outer ex msg",
                new RuntimeException(
                        "middle ex msg",
                        new RuntimeException("inner ex msg")));

        final String msg = unwrapMessage(throwable);

        LOGGER.info("msg: {}", msg);

        assertThat(msg)
                .contains("Outer ex msg")
                .contains("middle ex msg")
                .contains("inner ex msg");
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


    private void doTest(final Exception exception,
                        final StroomStatusCode stroomStatusCode,
                        final String msg) {
        doTest(exception, stroomStatusCode, new AttributeMap(), msg);
    }

    private String doTest(final Exception exception,
                          final StroomStatusCode stroomStatusCode,
                          final AttributeMap attributeMap,
                          final String msg) {
        final AtomicReference<String> msgRef = new AtomicReference<>();
        assertThatThrownBy(() -> {
            try {
                throw StroomStreamException.create(exception, attributeMap);
            } catch (final Exception e) {
                LOGGER.info("msg: {}", e.getMessage());
                msgRef.set(e.getMessage());
                throw e;
            }
        })
                .hasMessage("Stroom Status " + stroomStatusCode.getCode() + " - " +
                        stroomStatusCode.getMessage() + " - " + msg);

        return msgRef.get();
    }
}
