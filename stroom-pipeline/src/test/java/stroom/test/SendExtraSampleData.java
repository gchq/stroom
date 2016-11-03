/*
 * Copyright 2016 Crown Copyright
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

package stroom.test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import stroom.util.io.StreamUtil;
import stroom.util.thread.ThreadUtil;

public class SendExtraSampleData {
    private SendExtraSampleData() {
        // Private constructor.
    }

    public static void main(final String[] args) {
        try {
            String urlS = "http://localhost:8056/datafeed";

            URL url = new URL(urlS);

            String xml = StreamUtil
                    .streamToString(ClassLoader.getSystemResourceAsStream("samples/input/XML-EVENTS~1.in"));

            for (int i = 0; i < 100; i++) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> true);
                }
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/audit");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setChunkedStreamingMode(100);
                connection.addRequestProperty("Feed", "XML-EVENTS");
                connection.connect();

                OutputStream out = connection.getOutputStream();
                out.write(xml.getBytes());
                out.close();

                int response = connection.getResponseCode();
                String msg = connection.getResponseMessage();

                connection.disconnect();

                System.out.println("Client Got Response " + response);
                if (msg != null && msg.length() > 0) {
                    System.out.println(msg);
                }

                ThreadUtil.sleep(100);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
