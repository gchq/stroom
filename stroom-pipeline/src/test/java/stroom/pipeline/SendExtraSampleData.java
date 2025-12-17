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

package stroom.pipeline;

import stroom.util.io.StreamUtil;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SendExtraSampleData {

    private SendExtraSampleData() {
        // Private constructor.
    }

    public static void main(final String[] args) {
        final String url = "http://localhost:8056/datafeed";
        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {

            final String xml = StreamUtil
                    .streamToString(ClassLoader.getSystemResourceAsStream("samples/input/XML-EVENTS~1.in"));

            for (int i = 0; i < 100; i++) {
                final HttpPost httpPost = new HttpPost(url);
                httpPost.addHeader("Content-Type", "application/audit");
                httpPost.addHeader("Feed", "XML-EVENTS");
                httpPost.setEntity(new BasicHttpEntity(
                        new ByteArrayInputStream(xml.getBytes()),
                        ContentType.create("application/audit"),
                        true));
                httpClient.execute(httpPost, response -> {
                    final String msg = response.getReasonPhrase();
                    System.out.println("Client Got Response " + response.getCode());
                    if (msg != null && !msg.isEmpty()) {
                        System.out.println(msg);
                    }
                    return response.getCode();
                });

                Thread.sleep(100);
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

}
