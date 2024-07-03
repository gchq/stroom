package stroom.security.identity.account;

import stroom.security.shared.account.Account;
import stroom.security.shared.account.AccountResultPage;
import stroom.security.shared.account.FindAccountRequest;
import stroom.security.shared.account.CreateAccountRequest;
import stroom.security.shared.account.UpdateAccountRequest;

import java.util.Optional;

public interface AccountService {

    AccountResultPage list();

    AccountResultPage search(FindAccountRequest request);

    Account create(CreateAccountRequest request);

    Optional<Account> read(int accountId);

    Optional<Account> read(String email);

    void update(UpdateAccountRequest request, int accountId);

    void delete(int accountId);
}
