package stroom.proxy.app;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.ModelStringUtil;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

public class TestReceiveDataServlet {

    public static void main(final String[] args) {
        System.out.println("AVAILABLE PROCESSORS = " + Runtime.getRuntime().availableProcessors());

        final HttpClient httpClient = HttpClients.createDefault();

        final int threadCount = 10;
        final LongAdder count = new LongAdder();
        final CompletableFuture[] arr = new CompletableFuture[threadCount];

        final long startTime = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            arr[i] = CompletableFuture.runAsync(() -> {
                while (true) {
                    if (post(httpClient)) {
                        count.increment();
                    }
                }
            });
        }

        CompletableFuture.runAsync(() -> {
            long lastTime = startTime;
            long lastCount = 0;
            while (true) {
                ThreadUtil.sleep(10000);

                final long now = System.currentTimeMillis();
                final long totalCount = count.longValue();
                final long deltaCount = totalCount - lastCount;
                final double totalSeconds = (now - startTime) / 1000D;
                final double deltaSeconds = (now - lastTime) / 1000D;

                System.out.println("Posts " +
                        "Delta: " +
                        deltaCount +
                        " in " +
                        ModelStringUtil.formatDurationString(now - lastTime) +
                        " " +
                        (long) (deltaCount / deltaSeconds) + "pps" +
                        " " +
                        "Total: " + totalCount +
                        " in " +
                        ModelStringUtil.formatDurationString(now - startTime) +
                        " " +
                        (long) (totalCount / totalSeconds) + "pps");

                lastTime = now;
                lastCount = totalCount;
            }
        });
        CompletableFuture.allOf(arr).join();
    }

    private static boolean post(final HttpClient httpClient) {
        try {
            final HttpPost httpPost = new HttpPost("http://127.0.0.1:8090/stroom/noauth/datafeed");
            httpPost.addHeader("Feed", "TEST-EVENTS2");
            httpPost.addHeader("System", "EXAMPLE_SYSTEM");
            httpPost.addHeader("Environment", "EXAMPLE_ENVIRONMENT");
            httpPost.setEntity(
                    new InputStreamEntity(
                            new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));

            // Execute and get the response.
            final HttpResponse response = httpClient.execute(httpPost);
            final HttpEntity entity = response.getEntity();

            if (entity != null) {
                try (final InputStream inputStream = entity.getContent()) {
                    // do something useful
//                    System.out.println(StreamUtil.streamToString(inputStream));
                }
            }

            return response.getStatusLine().getStatusCode() == 200;
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        }
        return false;
    }
}
