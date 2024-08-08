/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.receive;


import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.mock.MockStore;
import stroom.feed.api.FeedStore;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ReceiveDataServlet;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The combination of mock and prod classes means this test needs its own
 * context.
 */
class TestReceiveDataServlet {

    @Inject
    private ReceiveDataServlet receiveDataServlet;
    @Inject
    private MockHttpServletRequest request;
    @Inject
    private MockHttpServletResponse response;
    @Inject
    private MockStore store;
    @Inject
    private FeedStore feedStore;

    @BeforeEach
    void init() {
        final Injector injector = Guice.createInjector(new TestBaseModule());
        injector.injectMembers(this);

        request.resetMock();
        response.resetMock();
        store.clear();

        if (feedStore.list().size() == 0) {
            feedStore.createDocument("TEST-FEED");
        }
    }

    @Test
    void testErrorNoParameters() throws IOException, ServletException {
        receiveDataServlet.doPost(request, response);
        checkError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Feed must be specified");
    }

    private void checkError(final int code, final String msg) {
        assertThat(response.getResponseCode())
                .isEqualTo(code);
        assertThat(response.getSendErrorMessage().contains(msg))
                .as("Expecting '" + msg + "' but was '" + response.getSendErrorMessage() + "'")
                .isTrue();
    }

    private void checkOK() {
        assertThat(response.getResponseCode())
                .as(response.getSendErrorMessage())
                .isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void testErrorCompressionInvalid() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("effectiveTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "UNKNOWN");
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Unknown compression");
        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);
    }

    @Test
    void testOkCompressionNone() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("effectiveTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "NONE");
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("SOME TEST DATA");
            assertThat(store.getStreamStoreCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void testOkWithQueryString() throws IOException, ServletException {
        request.setQueryString("feed=TEST-FEED" + "&periodStartTime=" + DateUtil.createNormalDateTimeString()
                + "&periodEndTime=" + DateUtil.createNormalDateTimeString());
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("SOME TEST DATA");
            assertThat(store.getStreamStoreCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void testOkCompressionBlank() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "");
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("SOME TEST DATA");
            assertThat(store.getStreamStoreCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void testOkCompressionNotStated() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("SOME TEST DATA");
            assertThat(store.getStreamStoreCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void testErrorCompressionGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        request.setInputStream("SOME TEST DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "format");
        // checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        // "Input is not in the .gz format");
    }

    @Test
    void testErrorCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");

        // Data needs to be big else it gets dropped
        request.setInputStream("SOME TEST DATA XXXXXXXXXXX XXXXXXXXXXXXXX XXXXXXXXXXXXXXXX".getBytes());

        receiveDataServlet.doPost(request, response);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Compressed stream invalid");
    }

    @Test
    void testEmptyCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");
        // Data needs to be big else it gets dropped
        request.setInputStream("SMALL DATA".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();
    }

    @Test
    void testOKCompressionGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = new ByteArrayInputStream("SOME TEST DATA".getBytes());
                final OutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            StreamUtil.streamToStream(inputStream, gzipOutputStream);
        }
        request.setInputStream(outputStream.toByteArray());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("SOME TEST DATA");
        }
    }

    @Test
    void testOKCompressionGZIP2() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = new ByteArrayInputStream("LINE1\n".getBytes());
                final OutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            StreamUtil.streamToStream(inputStream, gzipOutputStream);
        }
        try (final InputStream inputStream = new ByteArrayInputStream("LINE2\n".getBytes());
                final OutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            StreamUtil.streamToStream(inputStream, gzipOutputStream);
        }
        request.setInputStream(outputStream.toByteArray());

        receiveDataServlet.doPost(request, response);

        checkOK();

        try (final Source source = store.openSource(store.getLastMeta().getId())) {
            assertThat(SourceUtil.readString(source))
                    .isEqualTo("LINE1\nLINE2\n");
        }
    }

    @Test
    void testOKCompressionZeroContent() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader(StandardHeaderArguments.CONTENT_LENGTH.get(), "0");
        request.addHeader("compression", "GZIP");
        request.setInputStream("".getBytes());

        receiveDataServlet.doPost(request, response);

        checkOK();
    }

    @Test
    void testOKCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = new ByteArrayInputStream("SOME TEST DATA".getBytes());
                final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("TEST.txt"));
            StreamUtil.streamToStream(inputStream, zipOutputStream);
        }

        request.setInputStream(outputStream.toByteArray());

        receiveDataServlet.doPost(request, response);

        checkOK();
    }

    @Test
    void testIOErrorWhileWriteUncompressesd() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.setInputStream(new CorruptInputStream(
                new ByteArrayInputStream("SOME TEST DATA".getBytes()), 10));
        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);

        receiveDataServlet.doPost(request, response);

        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }

    @Test
    void testIOErrorWhileWriteGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = new ByteArrayInputStream("SOME TEST DATA".getBytes());
                final OutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            StreamUtil.streamToStream(inputStream, gzipOutputStream);
        }

        request.setInputStream(new CorruptInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()), 10));

        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);

        receiveDataServlet.doPost(request, response);

        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }

    @Test
    void testIOErrorWhileWriteZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = new ByteArrayInputStream("SOME TEST DATA".getBytes());
                final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("TEST.txt"));
            StreamUtil.streamToStream(inputStream, zipOutputStream);
        }

        request.setInputStream(new CorruptInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()), 10));

        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);
        receiveDataServlet.doPost(request, response);
        assertThat(store.getStreamStoreCount())
                .isEqualTo(0);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }
}
