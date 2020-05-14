/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.token;

// TODO make this immutable with a builder for the optional stuff
//   and a JsonCreator on the ctor, then get rid of as many boxed primitives as
//   possible
public class Token {
    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private Long updateTimeMs;
    private String createUser;
    private String updateUser;

    private String userEmail;
    private String tokenType;
    private String data;
    private Long expiresOnMs;
    private String comments;
    private boolean enabled;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(final String userEmail) {
        this.userEmail = userEmail;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(final String tokenType) {
        this.tokenType = tokenType;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public Long getExpiresOnMs() {
        return expiresOnMs;
    }

    public void setExpiresOnMs(final Long expiresOnMs) {
        this.expiresOnMs = expiresOnMs;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    //
//    public static final class TokenBuilder {
//        private Integer id;
//        private Integer version;
//        private Long createTimeMs;
//        private Long updateTimeMs;
//        private String createUser;
//        private String updateUser;
//
//        private String userEmail;
//        private String tokenType;
//        private String data;
//        private Long expiresOnMs;
//        private String comments;
//        private boolean enabled;
//
//        public TokenBuilder() {
//        }
//
//        public TokenBuilder id(int id) {
//            this.id = id;
//            return this;
//        }
//
//        public TokenBuilder userEmail(String userEmail) {
//            this.userEmail = userEmail;
//            return this;
//        }
//
//        public TokenBuilder tokenType(String tokenType) {
//            this.tokenType = tokenType;
//            return this;
//        }
//
//        public TokenBuilder data(String token) {
//            this.token = token;
//            return this;
//        }
//
//        public TokenBuilder expiresOn(String expiresOn) {
//            this.expiresOn = expiresOn;
//            return this;
//        }
//
//        public TokenBuilder issuedOn(String issuedOn) {
//            this.issuedOn = issuedOn;
//            return this;
//        }
//
//        public TokenBuilder issuedByUser(String issuedByUser) {
//            this.issuedByUser = issuedByUser;
//            return this;
//        }
//
//        public TokenBuilder enabled(boolean enabled) {
//            this.enabled = enabled;
//            return this;
//        }
//
//        public TokenBuilder updatedOn(String updatedOn) {
//            this.updatedOn = updatedOn;
//            return this;
//        }
//
//        public TokenBuilder updatedByUser(String updatedByUser) {
//            this.updatedByUser = updatedByUser;
//            return this;
//        }
//
//        public Token build() {
//            Token token = new Token();
//            token.setId(id);
//            token.setUserEmail(userEmail);
//            token.setTokenType(tokenType);
//            token.setToken(this.token);
//            token.setExpiresOn(expiresOn);
//            token.setIssuedOn(issuedOn);
//            token.setIssuedByUser(issuedByUser);
//            token.setEnabled(enabled);
//            token.setUpdatedOn(updatedOn);
//            token.setUpdatedByUser(updatedByUser);
//            return token;
//        }
//    }
}
