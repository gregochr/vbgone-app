package com.vbgone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.*;
import com.vbgone.service.AnalysisService;
import com.vbgone.service.BuildService;
import com.vbgone.service.GenerationService;
import com.vbgone.service.GitHubService;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MigrationController.class)
class MigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnalysisService analysisService;

    @MockitoBean
    private GenerationService generationService;

    @MockitoBean
    private BuildService buildService;

    @MockitoBean
    private GitHubService gitHubService;

    @MockitoBean
    private SessionStore sessionStore;

    private static final String SESSION_ID = "test-session-123";

    @Test
    void analyse_returns200WithAnalysisResult() throws Exception {
        when(analysisService.analyse(any(), any()))
                .thenReturn(new AnalysisResult(
                        SESSION_ID,
                        List.of(new ClassInfo("Form1", List.of("Add", "Subtract"), List.of(), Complexity.LOW)),
                        List.of("Form1"),
                        "One class found."
                ));

        mockMvc.perform(post("/api/migrate/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AnalyseRequest("Form1.vb", "Public Class Form1..."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.classes[0].name").value("Form1"))
                .andExpect(jsonPath("$.classes[0].complexity").value("LOW"))
                .andExpect(jsonPath("$.suggestedMigrationOrder[0]").value("Form1"))
                .andExpect(jsonPath("$.summary").value("One class found."));
    }

    @Test
    void generateInterface_returns200WithInterfaceResult() throws Exception {
        when(generationService.generateInterface(SESSION_ID, "Form1"))
                .thenReturn(new InterfaceResult(SESSION_ID, "Form1", "IForm1", "public interface IForm1 {}"));

        mockMvc.perform(post("/api/migrate/interface")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassRequest(SESSION_ID, "Form1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.className").value("Form1"))
                .andExpect(jsonPath("$.interfaceName").value("IForm1"))
                .andExpect(jsonPath("$.code").isString());
    }

    @Test
    void generateTests_returns200WithTestsResult() throws Exception {
        when(generationService.generateTests(SESSION_ID, "Form1"))
                .thenReturn(new TestsResult(SESSION_ID, "Form1", "Form1Tests", "[TestFixture]...", 30));

        mockMvc.perform(post("/api/migrate/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassRequest(SESSION_ID, "Form1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.className").value("Form1"))
                .andExpect(jsonPath("$.testClassName").value("Form1Tests"))
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.testCount").value(30));
    }

    @Test
    void generateStub_returns200WithStubResult() throws Exception {
        when(generationService.generateStub(SESSION_ID, "Form1"))
                .thenReturn(new StubResult(SESSION_ID, "Form1", "public class Form1 {}"));

        mockMvc.perform(post("/api/migrate/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassRequest(SESSION_ID, "Form1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.className").value("Form1"))
                .andExpect(jsonPath("$.code").isString());
    }

    @Test
    void build_returns200WithBuildResult() throws Exception {
        when(buildService.build(SESSION_ID))
                .thenReturn(new BuildResult(SESSION_ID, BuildStatus.RED, 30, 0, 30, List.of()));

        mockMvc.perform(post("/api/migrate/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BuildRequest(SESSION_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.buildStatus").value("RED"))
                .andExpect(jsonPath("$.total").value(30))
                .andExpect(jsonPath("$.passed").value(0))
                .andExpect(jsonPath("$.failed").value(30))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void implement_returns200WithImplementResult() throws Exception {
        when(generationService.implement(SESSION_ID, "Form1", ImplementMode.CLAUDE))
                .thenReturn(new ImplementResult(SESSION_ID, "Form1", "public class Form1 { ... }", ImplementMode.CLAUDE));

        mockMvc.perform(post("/api/migrate/implement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ImplementRequest(SESSION_ID, "Form1", ImplementMode.CLAUDE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.className").value("Form1"))
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.mode").value("CLAUDE"));
    }

    @Test
    void raisePR_returns200WithPullRequestResult() throws Exception {
        when(gitHubService.raisePR(SESSION_ID, "chrisgregory", "vbgone-output", "migrate/form1"))
                .thenReturn(new PullRequestResult(
                        SESSION_ID,
                        "https://github.com/chrisgregory/vbgone-output/pull/1",
                        "migrate/form1",
                        List.of("Form1/IForm1.cs", "Form1/Form1.cs", "Form1.Tests/Form1Tests.cs")
                ));

        mockMvc.perform(post("/api/migrate/pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PullRequestRequest(SESSION_ID, "chrisgregory", "vbgone-output", "migrate/form1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.prUrl").value("https://github.com/chrisgregory/vbgone-output/pull/1"))
                .andExpect(jsonPath("$.branchName").value("migrate/form1"))
                .andExpect(jsonPath("$.filesCommitted").isArray())
                .andExpect(jsonPath("$.filesCommitted.length()").value(3));
    }
}
