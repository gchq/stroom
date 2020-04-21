package stroom.authentication.account;

import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface AccountService {
    ResultPage<Account> list();

    ResultPage<Account> search(String email);

    Account create(CreateAccountRequest request);

    Optional<Account> read(int accountId);

    Optional<Account> read(String email);

    void update(Account account, int accountId);

    void delete(int accountId);
}
