/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.handler;


import jakarta.inject.Singleton;

@Singleton
public class MockForwardS3DestinationFactory implements ForwardS3DestinationFactory {

    private final MockForwardS3Destination mockForwardS3Destination;

    public MockForwardS3DestinationFactory() {
        this.mockForwardS3Destination = new MockForwardS3Destination();
    }

    @Override
    public ForwardDestination create(final ForwardS3Config forwardS3Config) {
        return mockForwardS3Destination;
    }

    public MockForwardS3Destination getMockForwardS3Destination() {
        return mockForwardS3Destination;
    }
}
