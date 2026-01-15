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

/*
 * This file was copied from https://github.com/dhatim/dropwizard-prometheus
 * at commit a674a1696a67186823a464383484809738665282 (v4.0.1-2)
 * and modified to work within the Stroom code base. All subsequent
 * modifications from the original are also made under the Apache 2.0 licence
 * and are subject to Crown Copyright.
 */

/*
 * Copyright 2025 github.com/dhatim
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

package stroom.dropwizard.common.prometheus;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class PushGateway implements PrometheusSender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PushGateway.class);

    private static final int SECONDS_PER_MILLISECOND = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(PushGateway.class);

    private final String url;
    private final String job;

    private volatile HttpURLConnection connection = null;
    private PrometheusTextWriter writer;
    private DropwizardMetricsExporter exporter;

    public PushGateway(final String url) {
        this(url, "prometheus");
    }

    public PushGateway(final String url, final String job) {
        this.url = url;
        this.job = job;
    }

    @Override
    public void close() throws IOException {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (final IOException e) {
            LOG.error("Error closing writer", e);
        } finally {
            this.writer = null;
            this.exporter = null;
        }

        final int response = connection.getResponseCode();
        if (response != HttpURLConnection.HTTP_ACCEPTED) {
            throw new IOException("Response code from " + url + " was " + response);
        }
        connection.disconnect();
        this.connection = null;
    }

    @Override
    public void connect() throws IOException {
        if (!isConnected()) {
            synchronized (this) {
                if (!isConnected()) {
                    final String targetUrl = url
                                             + "/metrics/job/"
                                             + URLEncoder.encode(job, StandardCharsets.UTF_8);
                    final HttpURLConnection conn = (HttpURLConnection) URI.create(targetUrl)
                            .toURL()
                            .openConnection();
                    conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, TextFormat.REQUEST_CONTENT_TYPE);
                    conn.setDoOutput(true);
                    conn.setRequestMethod(HttpMethod.POST);

                    conn.setConnectTimeout(10 * SECONDS_PER_MILLISECOND);
                    conn.setReadTimeout(10 * SECONDS_PER_MILLISECOND);
                    LOGGER.debug("Connecting to url: {}", url);
                    conn.connect();
                    this.writer = new PrometheusTextWriter(new BufferedWriter(new OutputStreamWriter(
                            conn.getOutputStream(),
                            StandardCharsets.UTF_8)));
                    this.exporter = new DropwizardMetricsExporter(writer);
                    this.connection = conn;
                }
            }
        }
    }

    @Override
    public void sendAppInfo() {
        exporter.writeAppInfo();
    }

    @Override
    public void sendGauge(final String name, final Gauge<?> gauge) {
        exporter.writeGauge(name, Collections.emptyMap(), gauge);
    }

    @Override
    public void sendCounter(final String name, final Counter counter) {
        exporter.writeCounter(name, Collections.emptyMap(), counter);
    }

    @Override
    public void sendHistogram(final String name, final Histogram histogram) {
        exporter.writeHistogram(name, Collections.emptyMap(), histogram);
    }

    @Override
    public void sendMeter(final String name, final Meter meter) {
        exporter.writeMeter(name, Collections.emptyMap(), meter);
    }

    @Override
    public void sendTimer(final String name, final Timer timer) {
        exporter.writeTimer(name, Collections.emptyMap(), timer);
    }

    @Override
    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public boolean isConnected() {
        return connection != null;
    }

    @Override
    public String toString() {
        return "PushGateway{" +
               "url='" + url + '\'' +
               ", job='" + job + '\'' +
               '}';
    }
}
