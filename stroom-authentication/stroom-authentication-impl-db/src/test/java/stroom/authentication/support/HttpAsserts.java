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
 */

package stroom.authentication.support;

import com.mashape.unirest.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpAsserts {
    public static void assertUnauthorised(HttpResponse response) {
        assertThat(response.getStatus()).isEqualTo(401);
    }

    public static void assertBadRequest(HttpResponse response) {
        assertThat(response.getStatus()).isEqualTo(400);
    }

    public static void assertUnprocessableEntity(HttpResponse response) {
        assertThat(response.getStatus()).isEqualTo(422);
    }

    public static void assertConflict(HttpResponse response) {
        assertThat(response.getStatus()).isEqualTo(409);
    }

    public static void assertOk(HttpResponse response) {
        assertThat(response.getStatus()).isEqualTo(200);
    }

    public static void assertBodyNotNull(HttpResponse response) {
        String body = (String) response.getBody();
        assertThat(body).isNotNull();
    }
}
