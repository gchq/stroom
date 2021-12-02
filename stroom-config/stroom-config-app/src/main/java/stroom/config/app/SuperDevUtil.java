package stroom.config.app;

import stroom.security.impl.ContentSecurityConfig;
import stroom.util.ColouredStringBuilder;
import stroom.util.ConsoleColour;
import stroom.util.shared.AbstractConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperDevUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuperDevUtil.class);

    private static final String GWT_SUPER_DEV_SYSTEM_PROP_NAME = "gwtSuperDevMode";
    private static final String SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE = "";
    private static final boolean SUPER_DEV_SESSION_COOKIE_SECURE_VALUE = false;

    public static boolean isInSuperDevMode() {
        return Boolean.getBoolean(GWT_SUPER_DEV_SYSTEM_PROP_NAME);
    }

    public static void showSuperDevBanner() {
        LOGGER.warn("" + ConsoleColour.red(
                "" +
                        "\n                                      _                                  _      " +
                        "\n                                     | |                                | |     " +
                        "\n      ___ _   _ _ __   ___ _ __    __| | _____   __  _ __ ___   ___   __| | ___ " +
                        "\n     / __| | | | '_ \\ / _ \\ '__|  / _` |/ _ \\ \\ / / | '_ ` _ \\ / _ \\ / _` |/ _ \\" +
                        "\n     \\__ \\ |_| | |_) |  __/ |    | (_| |  __/\\ V /  | | | | | | (_) | (_| |  __/" +
                        "\n     |___/\\__,_| .__/ \\___|_|     \\__,_|\\___| \\_/   |_| |_| |_|\\___/ \\__,_|\\___|" +
                        "\n               | |                                                              " +
                        "\n               |_|                                                              " +
                        "\n"));

        final String msg = new ColouredStringBuilder()
                .appendRed("In GWT Super Dev Mode, overriding ")
                .appendCyan(ContentSecurityConfig.PROP_NAME_CONTENT_SECURITY_POLICY)
                .appendRed(" to [")
                .appendCyan(SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE)
                .appendRed("] and ")
                .appendCyan(AppConfig.PROP_NAME_SESSION_COOKIE)
                .appendRed(" to [")
                .appendCyan(String.valueOf(SUPER_DEV_SESSION_COOKIE_SECURE_VALUE))
                .appendRed("] in appConfig")
                .toString();

        LOGGER.warn(msg);
    }

    public static AbstractConfig relaxSecurityInSuperDevMode(final AbstractConfig config) {
        if (config instanceof ContentSecurityConfig) {
            ContentSecurityConfig contentSecurityConfig = (ContentSecurityConfig) config;
            return contentSecurityConfig.withContentSecurityPolicy(SUPER_DEV_CONTENT_SECURITY_POLICY_VALUE);

        } else if (config instanceof SessionCookieConfig) {
            SessionCookieConfig sessionCookieConfig = (SessionCookieConfig) config;
            return sessionCookieConfig.withSecure(SUPER_DEV_SESSION_COOKIE_SECURE_VALUE);
        } else {
            return config;
        }
    }
}
