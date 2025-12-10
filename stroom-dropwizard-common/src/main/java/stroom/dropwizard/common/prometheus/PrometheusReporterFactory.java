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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.metrics.common.BaseReporterFactory;
import jakarta.validation.constraints.NotNull;

/**
 * Relies on the Service Loader Interface file
 * stroom-proxy/stroom-proxy-app/src/main/resources/META-INF/services/io.dropwizard.metrics.common.ReporterFactory
 * so that it can be found by {@link io.dropwizard.jackson.DiscoverableSubtypeResolver}.
 */
@JsonTypeName("prometheus")
public class PrometheusReporterFactory extends BaseReporterFactory {

    @JsonProperty
    @NotNull
    public String url = null;

    @JsonProperty
    @NotNull
    public String prefix = "";

    @JsonProperty
    @NotNull
    public String job = "prometheus";

    @Override
    public ScheduledReporter build(final MetricRegistry registry) {
        // TODO I think it is unlikely that we will use a push approach
        //  as the way dropwizard initialises the PushGateway, we have no means
        //  to inject anything with guice, e.g. to use the JerseyClientFactory
        //  for https etc., or AppInfoProvider.
        //  It should work on http and with no AppInfoProvider.
        final PushGateway pushgateway = new PushGateway(url, job);
        return PrometheusReporter.forRegistry(registry)
                .prefixedWith(prefix)
                .filter(getFilter())
                .build(pushgateway);
    }
}
