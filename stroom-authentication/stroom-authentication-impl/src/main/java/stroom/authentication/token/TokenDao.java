package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.authentication.exceptions.NoSuchUserException;

import java.time.Instant;
import java.util.Optional;

public interface TokenDao {
    Token create(Token token);

    String createEmailResetToken(String emailAddress, String clientId) throws NoSuchUserException;

    Token createToken(
            Token.TokenType tokenType,
            String issuingUserEmail,
            Instant expiryDateIfApiKey,
            String recipientUserEmail,
            String clientId,
            boolean isEnabled,
            String comment) throws NoSuchUserException;

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);

    SearchResponse searchTokens(SearchRequest searchRequest);
}
