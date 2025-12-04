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

package stroom.pipeline.xml;

/**
 * <p>
 * Holder class for the test data.
 * </p>
 */
public class DataProcess {
    private String feedName;
    private String resource;

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(final String resource) {
        this.resource = resource;
    }

}
