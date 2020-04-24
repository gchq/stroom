package stroom.authentication.token;

public class TokenEventLogImpl implements TokenEventLog {
    @Override
    public void search(final SearchRequest searchRequest, final SearchResponse searchResponse, final Throwable ex) {
        //        stroomEventLoggingService.createAction("SearchTokens", "The user searched for an API token");
    }

    @Override
    public void create(final CreateTokenRequest createTokenRequest, final Token token, final Throwable ex) {
//        stroomEventLoggingService.createAction("CreateUser", "Create a user");
    }

    @Override
    public void deleteAll(final int count, final Throwable ex) {
//        stroomEventLoggingService.createAction("DeleteAllApiTokens", "Delete all tokens");
    }

    @Override
    public void delete(final int tokenId, final int count, final Throwable ex) {
//        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by ID");
    }

    @Override
    public void delete(final String data, final int count, final Throwable ex) {
//        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by the value of the actual token.");
    }

    @Override
    public void read(final String data, final Token token, final Throwable ex) {
//        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the string value of the token.");
    }

    @Override
    public void read(final int tokenId, final Token token, final Throwable ex) {
//        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the token ID.");
    }

    @Override
    public void toggleEnabled(final int tokenId, final boolean isEnabled, final Throwable ex) {
//        stroomEventLoggingService.createAction("ToggleApiTokenEnabled", "Toggle whether a token is enabled or not.");
    }

    @Override
    public void getPublicKey(final Throwable ex) {
//        stroomEventLoggingService.createAction("GetPublicApiKey", "Read a token by the token ID.");
    }
}
