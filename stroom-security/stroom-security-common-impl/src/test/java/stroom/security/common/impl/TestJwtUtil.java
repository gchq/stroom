package stroom.security.common.impl;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;
import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestJwtUtil {

    @Mock
    private HttpServletRequest mockHttpServletRequest;

    @Test
    void testGetUserIdFromIdentities() {
        final String identities = "" +
                "[{" + "" +
                "\"userId\":\"test_user@somewhere.com\"," +
                "\"providerName\":\"UNKNOWN\"," +
                "\"providerType\":\"UNKNOWN\"," +
                "\"issuer\":\"https://some.issuer.com\"," +
                "\"primary\":true," +
                "\"dateCreated\":12345" +
                "}]";
        final String userId = JwtUtil.getUserIdFromIdentities(identities);
        assertThat(userId).isEqualTo("test_user@somewhere.com");
    }

    @Test
    void testRemovePrefix() {
        final String prefixed = "corp_test_user@somewhere.com";
        final String userId = JwtUtil.removePrefix(prefixed);
        assertThat(userId).isEqualTo("test_user@somewhere.com");
    }

    @TestFactory
    Stream<DynamicTest> getJwsFromHeader() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<String>>(){})
                .withTestFunction(testCase -> {
                    Mockito.when(mockHttpServletRequest.getHeader(Mockito.anyString()))
                            .thenReturn(testCase.getInput());
                    return JwtUtil.getJwsFromHeader(mockHttpServletRequest, "MyHeader");
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Optional.empty())
                .addCase("", Optional.empty())
                .addCase("   ", Optional.empty())
                .addCase("foo", Optional.of("foo"))
                .addCase(JwtUtil.BEARER_PREFIX + "foo", Optional.of("foo"))
                .build();
    }
}
