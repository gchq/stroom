package stroom.proxy.app;

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
                    count.increment();
                    post(httpClient);
                }
            });
        }

        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                } catch (final InterruptedException e) {
                    System.err.println(e.getMessage());
                }

                final double seconds = (System.currentTimeMillis() - startTime) / 1000D;
                final double num = count.longValue();
                System.out.println("Posts per second = " + (long) (num / seconds) + " (" + num + ")");
            }
        });
        CompletableFuture.allOf(arr).join();
    }

    private static void post(final HttpClient httpClient) {
        try {
            final HttpPost httpPost = new HttpPost("http://127.0.0.1:8090/stroom/noauth/datafeed");
            httpPost.addHeader("Feed", "TEST-EVENTS");
            httpPost.addHeader("System", "EXAMPLE_SYSTEM");
            httpPost.addHeader("Environment", "EXAMPLE_ENVIRONMENT");
            httpPost.setEntity(
                    new InputStreamEntity(
                            new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));

            // Execute and get the response.
            final HttpResponse response = httpClient.execute(httpPost);
            final HttpEntity entity = response.getEntity();

            if (entity != null) {
                try (InputStream instream = entity.getContent()) {
                    // do something useful
                }
            }
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
