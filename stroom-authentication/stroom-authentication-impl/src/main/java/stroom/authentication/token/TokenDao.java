package stroom.authentication.token;

import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.account.Account;

import java.time.Instant;
import java.util.Optional;

public interface TokenDao {

    SearchResponse searchTokens(SearchRequest searchRequest);

    String createEmailResetToken(String emailAddress, String clientId) throws NoSuchUserException;

    Token createIdToken(String idToken, String subject, long expiresOn);

    Token createToken(
            Token.TokenType tokenType,
            String issuingUserEmail,
            Instant expiryDateIfApiKey,
            String recipientUserEmail,
            String clientId,
            boolean isEnabled,
            String comment) throws NoSuchUserException;

    void deleteAllTokensExceptAdmins();

    void deleteTokenById(int tokenId);

    void deleteTokenByTokenString(String token);

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    void enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);
}
