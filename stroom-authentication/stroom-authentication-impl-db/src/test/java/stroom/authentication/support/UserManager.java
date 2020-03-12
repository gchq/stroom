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
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import stroom.authentication.resources.user.v1.User;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public class UserManager {
    private String rootUrl;
    private String meUrl;

    public final int createUser(User user, String jwsToken) throws UnirestException {
        String serializedUser = serialiseUser(user);
        HttpResponse response = Unirest
                .post(this.rootUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwsToken)
                .body(serializedUser)
                .asString();

        return Integer.parseInt((String) response.getBody());
    }

    public final User getUser(int userId, String jwsToken) throws UnirestException, IOException {
        String url = this.rootUrl + userId;
        HttpResponse response = Unirest
                .get(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwsToken)
                .asString();

        if (response.getStatus() != 404) {
            String body = (String) response.getBody();
            List<User> users = (List<User>) userListMapper().fromJson(body);

            if (users != null) {
                return users.get(0);
            } else return null;
        } else return null;
    }

    public final List<User> deserialiseUsers(String body) throws IOException {
        return (List<User>) userListMapper().fromJson(body);
    }

    public final User deserialiseUser(String body) throws IOException {
        return (User) userMapper().fromJson(body);
    }

    public final String serialiseUser(User user) {
        return new Moshi.Builder().build().adapter(User.class).toJson(user);
    }

    private JsonAdapter userListMapper() {
        Moshi moshi = new Moshi.Builder().build();
        ParameterizedType type = Types.newParameterizedType(List.class, User.class);
        JsonAdapter<List<User>> jsonAdapter = moshi.adapter(type);
        return jsonAdapter;
    }

    private JsonAdapter userMapper() {
        Moshi moshi = new Moshi.Builder().build();
        ParameterizedType type = Types.newParameterizedType(User.class);
        JsonAdapter<User> jsonAdapter = moshi.adapter(type);
        return jsonAdapter;
    }

    public void setPort(int appPort) {
        this.rootUrl = "http://localhost:" + appPort + "/user/v1/";
        this.meUrl = "http://localhost:" + appPort + "/user/v1/me";
    }

    public String getRootUrl() {
        return this.rootUrl;
    }

    public String getMeUrl() {
        return meUrl;
    }
}
