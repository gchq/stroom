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

package stroom.app.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.gwtplatform.mvp.client.DelayedBindRegistry;
import stroom.app.client.gin.DashboardAppGinjector;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class DashboardApp implements EntryPoint {
    public final DashboardAppGinjector ginjector = GWT.create(DashboardAppGinjector.class);

    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        // This is required for Gwt-Platform proxy's generator.
        DelayedBindRegistry.bind(ginjector);

        // Start the login manager. This will attempt to auto login with PKI and
        // will therefore start the rest of the application.
        ginjector.getLoginManager().autoLogin();

        // Remember how places were used in case we want to use URLs and history
        // at some point.
        // ginjector.getPlaceManager().revealCurrentPlace();
    }

}
