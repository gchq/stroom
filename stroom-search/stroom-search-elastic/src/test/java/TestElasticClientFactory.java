/*
 * Copyright 2021 Crown Copyright
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

import stroom.search.elastic.ElasticClientFactory;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticClientFactory {

    @Test
    public void testHostFromUrl() {
        // Scheme and hostname
        final String hostName = "elastic.example.com.au";
        String url = "https://" + hostName;
        HttpHost host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("Valid host is returned")
                .isNotNull();
        assertThat(host.getSchemeName())
                .isEqualTo("https");
        assertThat(host.getHostName())
                .isEqualTo(hostName);

        // Scheme, hostname and port
        final int port = 9200;
        //noinspection HttpUrlsUsage
        url = "http://" + hostName + ":9200";
        host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("Valid host is returned")
                .isNotNull();
        assertThat(host.getHostName())
                .isEqualTo(hostName);
        assertThat(host.getPort())
                .isEqualTo(port);

        // Invalid URL
        url = hostName;
        host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("No host is returned for an invalid URL")
                .isNull();
    }
}
