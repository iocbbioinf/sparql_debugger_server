package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.service.SparqlEndpointServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DebuggerConrollerTest {

    @Autowired
    private MockMvc mockMvc;

    @Spy
    private HttpClient mockHttpClient;

    @InjectMocks
    private SparqlEndpointServiceImpl yourAsyncService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        setupMockHttpClient();
    }

    private void setupMockHttpClient() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        // Assuming the response body is a string and the status code is 200
        when(mockResponse.body()).thenReturn("Mock response");
        when(mockResponse.statusCode()).thenReturn(200);

        // Setup the mock to return a completed future holding our mock response
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
    }

//    @Test
    public void testDebugServicePost() throws Exception {
        mockMvc.perform(post("/service")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .param("endpoint", "http://example.com")
                        .param("queryId", "1")
                        .param("parentEndpointNodeId", "10")
                        .param("subqueryId", "15")
                        .param("query", "SELECT * WHERE {?s ?p ?o}")
                        .param("namedGraphUri", "http://example.com/graph")
                        .param("defaultGraphUri", "http://example.com/default")
                        .content("query data")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

//    @Test
    public void testDebugServiceGet() throws Exception {
        mockMvc.perform(get("/service")
                        .param("endpoint", "http://example.com")
                        .param("queryId", "1")
                        .param("parentEndpointNodeId", "10")
                        .param("subqueryId", "15")
                        .param("query", "SELECT * WHERE {?s ?p ?o}")
                        .param("namedGraphUri", "http://example.com/graph")
                        .param("defaultGraphUri", "http://example.com/default"))
                .andExpect(status().isOk());
    }

    // Add more tests for other endpoints similarly
}