/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.startup;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.Config;

import static org.apache.shiro.web.filter.mgt.DefaultFilter.port;

public class App extends Application<Config> {
    private final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/ui", "/", "stroom.jsp", "ui"));
    }

    @Override
    public void run(Config configuration, io.dropwizard.setup.Environment environment) throws Exception {
        // The order in which the following are run is important.
        Environment.configure(configuration, environment);
        SpringContexts springContexts = new SpringContexts();
        Servlets servlets = new Servlets(environment.getApplicationContext());
        Filters filters = new Filters(environment.getApplicationContext());
        Listeners listeners = new Listeners(environment.servlets(), springContexts.rootContext);
        springContexts.start(environment, configuration);
        Resources resources = new Resources(environment.jersey(), servlets.upgradeDispatcherServletHolder);
        HealthChecks healthChecks = new HealthChecks(environment.healthChecks(), resources);

        createServiceDiscovery(configuration, "TODO: IPADDRESS");
    }

    private void createServiceDiscovery(Config config, String ipAddress) {
        LOGGER.info("Starting Curator client using Zookeeper at '{}'...", config.getZookeeperUrl());
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(config.getZookeeperUrl(), retryPolicy);
        client.start();

        try {
            LOGGER.info("Setting up instance for '{}' service, running on '{}:{}'...", "stroom", ipAddress, port);
            ServiceInstance<String> instance = ServiceInstance.<String>builder()
                    .serviceType(ServiceType.PERMANENT)
                    .name("stroom")
                    .address(ipAddress)
                    .port(8080)
                    .build();

            ServiceDiscovery serviceDiscovery = ServiceDiscoveryBuilder
                    .builder(String.class)
                    .client(client)
                    .basePath("stroom-services")
                    .thisInstance(instance)
                    .build();

            serviceDiscovery.start();
            LOGGER.info("Service instance created successfully!");
        } catch (Exception e){
            LOGGER.error("Service instance creation failed! ", e);
        }
    }
}
