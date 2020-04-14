package stroom.authentication.authenticate.api;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface AuthSession {
    Optional<String> currentSubject(HttpServletRequest request);
}
