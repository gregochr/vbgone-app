package com.vbgone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VbGoneApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void applicationNameIsConfigured() {
        String appName = context.getEnvironment().getProperty("spring.application.name");
        assertThat(appName).isEqualTo("vbgone-app");
    }

    @Test
    void serverPortIsConfigured() {
        String port = context.getEnvironment().getProperty("server.port");
        assertThat(port).isEqualTo("8080");
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/migrate/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mainMethodDoesNotThrow() {
        // Verify the main method can be invoked without error
        VbGoneApplication.main(new String[]{});
    }
}
