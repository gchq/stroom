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

package stroom.security.identity.exceptions;

import stroom.security.identity.token.TokenType;

public class TokenCreationException extends RuntimeException {

    private TokenType tokenType;
    private String errorMessage;

    public TokenCreationException(Exception e) {
        super(e);
    }

    public TokenCreationException(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public TokenCreationException(TokenType tokenType, String errorMessage) {
        this.tokenType = tokenType;
        this.errorMessage = errorMessage;
    }
}
