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

package stroom.dropwizard.common;

import stroom.util.http.HttpClientConfigConverter;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;

import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public class DropwizardHttpClientFactory implements HttpClientFactory {

    private final Environment environment;
    private final HttpClientConfigConverter httpClientConfigConverter;

    @Inject
    public DropwizardHttpClientFactory(final Environment environment,
                                       final HttpClientConfigConverter httpClientConfigConverter) {
        this.environment = environment;
        this.httpClientConfigConverter = httpClientConfigConverter;
    }

    @Override
    public CloseableHttpClient get(final String name,
                                   final HttpClientConfiguration httpClientConfiguration) {
        // Now create a new client.
        io.dropwizard.client.HttpClientConfiguration configuration = httpClientConfigConverter.convert(
                httpClientConfiguration,
                io.dropwizard.client.HttpClientConfiguration.class);
        if (configuration == null) {
            configuration = new io.dropwizard.client.HttpClientConfiguration();
        }
        return new HttpClientBuilder(environment)
                .using(configuration)
                .build(name);
    }
}
