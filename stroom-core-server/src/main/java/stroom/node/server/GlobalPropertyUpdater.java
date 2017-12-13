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

package stroom.node.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.shared.FindGlobalPropertyCriteria;
import stroom.node.shared.GlobalProperty;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.List;

/**
 * A Spring bean that is responsible for periodically polling the database to
 * get current settings for global properties and updating both the current
 * GlobalProperties instance and StroomProperties.
 */
@Component
public class GlobalPropertyUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalPropertyUpdater.class);

    private final GlobalPropertyService globalPropertyService;

    @Inject
    public GlobalPropertyUpdater(final GlobalPropertyService globalPropertyService) {
        this.globalPropertyService = globalPropertyService;
    }

    @StroomStartup
    public void started() {
        LOGGER.info("Updating StroomProperties from GlobalProperties");
        update();
        LOGGER.info("Finished updating StroomProperties from GlobalProperties");
    }

    /**
     * Refresh in background
     */
    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Property Cache Reload", description = "Reload properties in the cluster")
    public void update() {
        final GlobalProperties globalProperties = GlobalProperties.getInstance();
        final List<GlobalProperty> list = globalPropertyService.find(new FindGlobalPropertyCriteria());
        list.stream()
                .filter(prop -> prop.getName() != null && prop.getValue() != null)
                .forEach(prop -> {
                    final GlobalProperty existing = globalProperties.getGlobalProperty(prop.getName());
                    if (existing != null) {
                        existing.setValue(prop.getValue());

                        if (prop.getValue() != null) {
                            StroomProperties.setProperty(prop.getName(), prop.getValue(), StroomProperties.Source.DB);
                        }
                    }
                });
    }
}
