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

package stroom.status.remoteclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import stroom.status.remote.GetStatusRequest;
import stroom.status.remote.GetStatusResponse;
import stroom.status.remote.GetStatusResponse.StatusEntry;
import stroom.status.remote.RemoteStatusService;

public class RunStatusService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunStatusService.class);

    public static void main(String[] args) {
        try {
            ApplicationContext appContext = new ClassPathXmlApplicationContext(
                    new String[] { "classpath:META-INF/spring/stroomRemoteClientContext.xml" });

            RemoteStatusService statusService = appContext.getBean(RemoteStatusService.class);

            GetStatusResponse response = statusService.getStatus(new GetStatusRequest());

            for (StatusEntry statusEntry : response.getStatusEntryList()) {
                LOGGER.info(statusEntry.toString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
