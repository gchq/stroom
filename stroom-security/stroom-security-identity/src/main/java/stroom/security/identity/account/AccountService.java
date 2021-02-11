package stroom.security.identity.account;

import java.util.Optional;

public interface AccountService {
    AccountResultPage list();

    AccountResultPage search(SearchAccountRequest request);

    Account create(CreateAccountRequest request);

    Optional<Account> read(int accountId);

    Optional<Account> read(String email);

    void update(UpdateAccountRequest request, int accountId);

    void delete(int accountId);
}
