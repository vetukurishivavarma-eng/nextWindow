package com.nextwindow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextwindow.common.RateLimiter;
import com.nextwindow.monitoring.fetch.HostThrottle;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base for integration tests: full application context against a real PostgreSQL database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected RateLimiter rateLimiter;
    @Autowired protected HostThrottle throttle;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE alert, check_run, snapshot, watch, push_subscription, monitoring_source, auth_session, app_user, demo_page RESTART IDENTITY CASCADE");
        rateLimiter.reset();
        throttle.clear();
    }

    protected Cookie register(String email) throws Exception {
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register").header("X-Requested-With", "NextWindow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isCreated()).andReturn();
        return r.getResponse().getCookie("nw_session");
    }

    protected ResultActions get(Cookie c, String url) throws Exception {
        MockHttpServletRequestBuilder b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        if (c != null) b.cookie(c);
        return mvc.perform(b);
    }

    protected ResultActions send(String method, Cookie c, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder b = switch (method) {
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
        };
        b.header("X-Requested-With", "NextWindow").contentType(MediaType.APPLICATION_JSON);
        if (body != null) b.content(body instanceof String s ? s : mapper.writeValueAsString(body));
        if (c != null) b.cookie(c);
        return mvc.perform(b);
    }

    protected ResultActions post(Cookie c, String url, Object body) throws Exception {
        return send("POST", c, url, body);
    }

    protected JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    protected JsonNode createWatch(Cookie c, String body) throws Exception {
        return json(post(c, "/api/watches", body).andExpect(status().isCreated()));
    }

    protected Long sourceIdOf(JsonNode watch) {
        return watch.get("source").get("id").asLong();
    }
}
