/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.shared;

import stroom.entity.shared.Action;
import stroom.util.shared.SharedBoolean;

public class AcknowledgeSplashAction extends Action<SharedBoolean> {
    private static final long serialVersionUID = 1451964889275627717L;

    private String message;
    private String version;

    public AcknowledgeSplashAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public AcknowledgeSplashAction(final String message, final String version) {
        this.message = message;
        this.version = version;
    }

    public String getMessage() {
        return message;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String getTaskName() {
        return "Acknowledge splash";
    }
}
