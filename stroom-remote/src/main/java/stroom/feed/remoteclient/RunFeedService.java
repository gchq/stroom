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

package stroom.feed.remoteclient;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import stroom.feed.remote.GetFeedStatusRequest;
import stroom.feed.remote.RemoteFeedService;

public class RunFeedService {
    public static void main(String[] args) {
        try {
            ApplicationContext appContext = new ClassPathXmlApplicationContext(
                    "classpath:META-INF/spring/stroomRemoteContext.xml",
                    "classpath:META-INF/spring/stroomRemoteClientContext.xml");

            RemoteFeedService feedService = appContext.getBean(RemoteFeedService.class);

            feedService.getFeedStatus(new GetFeedStatusRequest());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
