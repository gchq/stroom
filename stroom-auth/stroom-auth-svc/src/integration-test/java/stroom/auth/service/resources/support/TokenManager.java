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

package stroom.auth.service.resources.support;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import org.eclipse.jetty.http.HttpStatus;
import stroom.auth.resources.token.v1.CreateTokenRequest;
import stroom.auth.resources.token.v1.SearchRequest;
import stroom.auth.resources.token.v1.SearchResponse;
import stroom.auth.resources.token.v1.Token;
import stroom.auth.resources.token.v1.Token.TokenType;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TokenManager {
    private String rootUrl;

    public final String createToken(String userEmail, TokenType tokenType, String securityToken) throws UnirestException, IOException {
        CreateTokenRequest createTokenRequest = new CreateTokenRequest(
                userEmail, tokenType.getText(), true, "Created by TokenManager, for integration tests");
        String serialisedCreateTokenRequest = serialise(createTokenRequest);
        HttpResponse response = Unirest
                .post(this.rootUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + securityToken)
                .body(serialisedCreateTokenRequest)
                .asString();
        return (String) response.getBody();
    }

    public void deleteToken(String token, String securityToken) throws UnirestException {
        HttpResponse response = Unirest
                .delete(this.rootUrl + "/byToken/" + token)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + securityToken)
                .asString();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    }

    public final void deleteAllTokens(String jwsToken) throws UnirestException {
        HttpResponse response = Unirest
                .delete(this.rootUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwsToken)
                .asString();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    }

    public final SearchResponse deserialiseTokens(String body) throws IOException {
        return (SearchResponse) searchResponseMapper().fromJson(body);
    }

    public final Token deserialiseToken(String body) throws IOException {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<Token> jsonAdapter = moshi.adapter(Token.class);
        return jsonAdapter.fromJson(body);
    }

    public final String serialiseToken(Token token) {
        return new Moshi.Builder().build().adapter(Token.class).toJson(token);
    }

    public final String serialise(CreateTokenRequest createTokenRequest) {
        return new Moshi.Builder().build().adapter(CreateTokenRequest.class).toJson(createTokenRequest);
    }

    public final CreateTokenRequest deserialiseCreateTokenRequest(String serialisedCreateTokenRequest) throws IOException {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<CreateTokenRequest> jsonAdapter = moshi.adapter(CreateTokenRequest.class);
        return jsonAdapter.fromJson(serialisedCreateTokenRequest);
    }

    public final String serialiseSearchRequest(SearchRequest searchRequest) {
        return new Moshi.Builder().build().adapter(SearchRequest.class).toJson(searchRequest);
    }

    public void setPort(int appPort) {
        this.rootUrl = "http://localhost:" + appPort + "/token/v1";
    }

    public String getRootUrl() {
        return rootUrl;
    }

    private JsonAdapter tokenListMapper() {
        Moshi moshi = new Moshi.Builder().build();
        ParameterizedType type = Types.newParameterizedType(List.class, Token.class);
        JsonAdapter<List<Token>> jsonAdapter = moshi.adapter(type);
        return jsonAdapter;
    }

    private JsonAdapter searchResponseMapper() {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<SearchResponse> jsonAdapter = moshi.adapter(SearchResponse.class);
        return jsonAdapter;
    }
}
