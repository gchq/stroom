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

package stroom.cluster.server;

import org.springframework.remoting.caucho.HessianServiceExporter;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Component(ClusterCallServiceRPC.BEAN_NAME)
public class ClusterCallServiceRPC extends HessianServiceExporter {
    public static final String BEAN_NAME = "clusterCallServiceRPC";

    @Inject
    ClusterCallServiceRPC(@Named("clusterCallServiceLocal") final ClusterCallService clusterCallService) {
        setService(clusterCallService);
        setServiceInterface(ClusterCallService.class);
    }
}
