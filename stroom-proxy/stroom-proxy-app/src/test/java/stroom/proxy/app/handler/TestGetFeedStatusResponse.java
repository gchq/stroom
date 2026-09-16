/*
 * Copyright 2026 Crown Copyright
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

import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code GetFeedStatusResponse}'s no-arg constructor defaulted the status to
 * {@link FeedStatus#Receive} while its {@code @JsonCreator} - the only route by which a status-less
 * response is actually built, and the one {@code RemoteFeedStatusClient} uses - left it null. So the
 * class held two contradictory answers to "what does an absent status mean?", and the one that ran
 * produced a null that became an opaque {@code NullPointerException} in
 * {@code FeedStatusAttributeMapFilter}'s switch, deep in the receive path.
 * <p>
 * The response is now refused at the boundary. That matters because of where the refusal lands:
 * {@code RemoteFeedStatusClient.callFeedStatus} reads the entity inside a {@code catch (Throwable)}
 * that turns any failure into a {@code FeedStatusUnavailableException}, which is exactly what the
 * last-good-response and {@code fallbackReceiveAction} paths exist to handle. A malformed downstream
 * response therefore becomes the operator's configured choice rather than an accident.
 * </p>
 * <p>
 * It lives in this package rather than beside the class because {@code stroom-proxy-remote-api} has
 * no test source set, and because the deserialisation that matters is the one done here.
 * </p>
 */
class TestGetFeedStatusResponse {

    private static final JsonMapper MAPPER = JsonUtil.getMapper();

    @Test
    void testAResponseThatStatesNoStatusIsRefusedRatherThanReadAsReceive() {
        assertThatThrownBy(() -> MAPPER.readValue(
                "{\"message\":\"something went wrong downstream\"}", GetFeedStatusResponse.class))
                .hasMessageContaining("must state a status");
    }

    @Test
    void testAnExplicitNullStatusIsRefusedToo() {
        assertThatThrownBy(() -> MAPPER.readValue(
                "{\"status\":null}", GetFeedStatusResponse.class))
                .hasMessageContaining("must state a status");
    }

    /**
     * The other half: tightening the creator must not stop a well-formed response being read.
     */
    @Test
    void testAWellFormedResponseIsStillRead() {
        final GetFeedStatusResponse response = MAPPER.readValue(
                "{\"status\":\"Drop\",\"message\":\"not for us\"}", GetFeedStatusResponse.class);

        assertThat(response.getStatus()).isEqualTo(FeedStatus.Drop);
        assertThat(response.getMessage()).isEqualTo("not for us");
    }
}
