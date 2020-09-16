package stroom.security.identity.account;

import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface AccountService {
    ResultPage<Account> list();

    ResultPage<Account> search(SearchAccountRequest request);

    Account create(CreateAccountRequest request);

    Optional<Account> read(int accountId);

    Optional<Account> read(String email);

    void update(UpdateAccountRequest request, int accountId);

    void delete(int accountId);
}
