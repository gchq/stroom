package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.authentication.exceptions.NoSuchUserException;

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

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);
}
