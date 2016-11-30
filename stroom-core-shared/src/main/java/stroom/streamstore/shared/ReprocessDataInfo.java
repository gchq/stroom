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

package stroom.streamstore.shared;

import stroom.util.shared.Severity;
import stroom.util.shared.SharedObject;

public class ReprocessDataInfo implements SharedObject {
    private static final long serialVersionUID = 7549523500761890727L;

    private Severity severity;
    private String message;
    private String details;

    public ReprocessDataInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    public ReprocessDataInfo(Severity severity, String message, String details) {
        this.severity = severity;
        this.message = message;
        this.details = details;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
