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
 *
 */

package stroom.datafeed;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import stroom.feed.StroomHeaderArguments;
import stroom.streamstore.MockStreamStore;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The combination of mock and prod classes means this test needs its own
 * context.
 */
//@ContextConfiguration(classes = {TestDataFeedServiceImplConfiguration.class})
//@Ignore("TODO 2015-11-18: These tests have interdependencies: they pass individually but fail when run together. Ignoring so the test may be fixed later.")
public class TestDataFeedServiceImpl extends TestBase {
    @Inject
    private DataFeedServlet dataFeedService;
//    @Inject
//    private FeedService feedService;
    @Inject
    private MockHttpServletRequest request;
    @Inject
    private MockHttpServletResponse response;
    @Inject
    private MockStreamStore streamStore;

    @Before
    public void init() {
        request.resetMock();
        response.resetMock();
        streamStore.clear();
//        final FeedDoc referenceFeed = feedService.create("TEST-FEED");
//        referenceFeed.setStatus(FeedStatus.RECEIVE);
//        referenceFeed.setReference(true);
//        feedService.save(referenceFeed);
    }

    @Test
    public void testErrorNoParameters() throws IOException, ServletException {
        dataFeedService.doPost(request, response);
        checkError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Feed must be specified");
    }

    private void checkError(final int code, final String msg) {
        Assert.assertEquals(code, response.getResponseCode());
        Assert.assertTrue("Expecting '" + msg + "' but was '" + response.getSendErrorMessage() + "'",
                response.getSendErrorMessage().contains(msg));
    }

    private void checkOK() {
        Assert.assertEquals(response.getSendErrorMessage(), HttpServletResponse.SC_OK, response.getResponseCode());
    }

    @Test
    public void testErrorCompressionInvalid() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("effectiveTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "UNKNOWN");
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Unknown compression");
        Assert.assertEquals(0, streamStore.getStreamStoreCount());
    }

    @Test
    public void testOkCompressionNone() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("effectiveTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "NONE");
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("SOME TEST DATA", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
        Assert.assertEquals(1, streamStore.getStreamStoreCount());
    }

    @Test
    public void testOkWithQueryString() throws IOException, ServletException {
        request.setQueryString("feed=TEST-FEED" + "&periodStartTime=" + DateUtil.createNormalDateTimeString()
                + "&periodEndTime=" + DateUtil.createNormalDateTimeString());
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("SOME TEST DATA", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
        Assert.assertEquals(1, streamStore.getStreamStoreCount());
    }

    @Test
    public void testOkCompressionBlank() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "");
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("SOME TEST DATA", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
        Assert.assertEquals(1, streamStore.getStreamStoreCount());
    }

    @Test
    public void testOkCompressionNotStated() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("SOME TEST DATA", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
        Assert.assertEquals(1, streamStore.getStreamStoreCount());
    }

    @Test
    public void testErrorCompressionGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        request.setInputStream("SOME TEST DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "format");
        // checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        // "Input is not in the .gz format");
    }

    @Test
    public void testErrorCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");

        // Data needs to be big else it gets dropped
        request.setInputStream("SOME TEST DATA XXXXXXXXXXX XXXXXXXXXXXXXX XXXXXXXXXXXXXXXX".getBytes());

        dataFeedService.doPost(request, response);

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Compressed stream invalid - No Zip Entries");
    }

    @Test
    public void testEmptyCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");
        // Data needs to be big else it gets dropped
        request.setInputStream("SMALL DATA".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();
    }

    @Test
    public void testOKCompressionGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(new ByteArrayInputStream("SOME TEST DATA".getBytes()),
                new GZIPOutputStream(outputStream));
        request.setInputStream(outputStream.toByteArray());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("SOME TEST DATA", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
    }

    @Test
    public void testOKCompressionGZIP2() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(new ByteArrayInputStream("LINE1\n".getBytes()), new GZIPOutputStream(outputStream));
        StreamUtil.streamToStream(new ByteArrayInputStream("LINE2\n".getBytes()), new GZIPOutputStream(outputStream));
        request.setInputStream(outputStream.toByteArray());

        dataFeedService.doPost(request, response);

        checkOK();

        Assert.assertEquals("LINE1\nLINE2\n", StreamUtil
                .streamToString(streamStore.openStreamSource(streamStore.getLastStream().getId()).getInputStream()));
    }

    @Test
    public void testOKCompressionZeroContent() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader(StroomHeaderArguments.CONTENT_LENGTH, "0");
        request.addHeader("compression", "GZIP");
        request.setInputStream("".getBytes());

        dataFeedService.doPost(request, response);

        checkOK();
    }

    @Test
    public void testOKCompressionZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        zipOutputStream.putNextEntry(new ZipEntry("TEST.txt"));
        StreamUtil.streamToStream(new ByteArrayInputStream("SOME TEST DATA".getBytes()), zipOutputStream);
        request.setInputStream(outputStream.toByteArray());

        dataFeedService.doPost(request, response);

        checkOK();
    }

    @Test
    public void testIOErrorWhileWriteUncompressesd() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.setInputStream(new CorruptInputStream(new ByteArrayInputStream("SOME TEST DATA".getBytes()), 10));
        Assert.assertEquals(0, streamStore.getStreamStoreCount());

        dataFeedService.doPost(request, response);

        Assert.assertEquals(0, streamStore.getStreamStoreCount());

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }

    @Test
    public void testIOErrorWhileWriteGZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "GZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(new ByteArrayInputStream("SOME TEST DATA".getBytes()),
                new GZIPOutputStream(outputStream));
        request.setInputStream(new CorruptInputStream(new ByteArrayInputStream(outputStream.toByteArray()), 10));

        Assert.assertEquals(0, streamStore.getStreamStoreCount());

        dataFeedService.doPost(request, response);

        Assert.assertEquals(0, streamStore.getStreamStoreCount());

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }

    @Test
    public void testIOErrorWhileWriteZIP() throws IOException, ServletException {
        request.addHeader("feed", "TEST-FEED");
        request.addHeader("periodStartTime", DateUtil.createNormalDateTimeString());
        request.addHeader("periodEndTime", DateUtil.createNormalDateTimeString());
        request.addHeader("compression", "ZIP");
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        zipOutputStream.putNextEntry(new ZipEntry("TEST.txt"));
        StreamUtil.streamToStream(new ByteArrayInputStream("SOME TEST DATA".getBytes()), zipOutputStream);
        request.setInputStream(new CorruptInputStream(new ByteArrayInputStream(outputStream.toByteArray()), 10));

        Assert.assertEquals(0, streamStore.getStreamStoreCount());
        dataFeedService.doPost(request, response);
        Assert.assertEquals(0, streamStore.getStreamStoreCount());

        checkError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected IO Junit Error at byte ");
    }
}
