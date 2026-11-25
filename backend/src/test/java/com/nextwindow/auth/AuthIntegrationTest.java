package com.nextwindow.auth;

import com.nextwindow.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginMeLogout() throws Exception {
        Cookie session = register("Alice@Example.com");
        assertThat(session.isHttpOnly()).isTrue();
        get(session, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"));

        var login = post(null, "/api/auth/login", "{\"email\":\"alice@example.com\",\"password\":\"correct-horse-1\"}")
                .andExpect(status().isOk()).andReturn();
        Cookie second = login.getResponse().getCookie("nw_session");
        assertThat(second.getValue()).isNotEqualTo(session.getValue());

        post(second, "/api/auth/logout", null).andExpect(status().isNoContent());
        get(second, "/api/auth/me").andExpect(status().isUnauthorized());
        get(session, "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void passwordsAreHashedNotStored() throws Exception {
        register("bob@example.com");
        String hash = jdbc.queryForObject("select password_hash from app_user", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("correct-horse-1");
        String token = jdbc.queryForObject("select token_hash from auth_session", String.class);
        assertThat(token).hasSize(64);
    }

    @Test
    void rejectsDuplicatesWeakPasswordsAndBadCredentials() throws Exception {
        register("carol@example.com");
        post(null, "/api/auth/register", "{\"email\":\"CAROL@example.com\",\"password\":\"another-pass-1\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        post(null, "/api/auth/register", "{\"email\":\"dan@example.com\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        post(null, "/api/auth/register", "{\"email\":\"dan@example.com\",\"password\":\"onlyletters\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        post(null, "/api/auth/register", "{\"email\":\"not-an-email\",\"password\":\"correct-horse-1\"}")
                .andExpect(status().isBadRequest());
        post(null, "/api/auth/login", "{\"email\":\"carol@example.com\",\"password\":\"wrong-pass-1\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        post(null, "/api/auth/login", "{\"email\":\"nobody@example.com\",\"password\":\"wrong-pass-1\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireSessionAndCsrfHeader() throws Exception {
        get(null, "/api/watches").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        get(new Cookie("nw_session", "forged-token"), "/api/watches").andExpect(status().isUnauthorized());
        Cookie session = register("erin@example.com");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/alerts/read-all").cookie(session).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        get(null, "/api/config").andExpect(status().isOk()).andExpect(jsonPath("$.demoEnabled").value(true));
        get(null, "/actuator/health").andExpect(status().isOk());
    }

    @Test
    void loginIsRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            post(null, "/api/auth/login", "{\"email\":\"x@example.com\",\"password\":\"bad-pass-1\"}").andExpect(status().isUnauthorized());
        }
        post(null, "/api/auth/login", "{\"email\":\"x@example.com\",\"password\":\"bad-pass-1\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void changePasswordRevokesOtherSessions() throws Exception {
        Cookie a = register("frank@example.com");
        Cookie b = post(null, "/api/auth/login", "{\"email\":\"frank@example.com\",\"password\":\"correct-horse-1\"}")
                .andReturn().getResponse().getCookie("nw_session");
        post(a, "/api/account/password", "{\"currentPassword\":\"wrong-1234\",\"newPassword\":\"new-secret-22\"}")
                .andExpect(status().isBadRequest());
        post(a, "/api/account/password", "{\"currentPassword\":\"correct-horse-1\",\"newPassword\":\"new-secret-22\"}")
                .andExpect(status().isNoContent());
        get(a, "/api/auth/me").andExpect(status().isOk());
        get(b, "/api/auth/me").andExpect(status().isUnauthorized());
    }
}
