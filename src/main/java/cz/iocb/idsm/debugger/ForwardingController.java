package cz.iocb.idsm.debugger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class ForwardingController {

    private final RestTemplate restTemplate;
    private final WebClient webClient;

    @Autowired
    public ForwardingController(RestTemplate restTemplate,  WebClient.Builder webClientBuilder) {
        this.restTemplate = restTemplate;
//        webClient = webClientBuilder.baseUrl("google.com").build();
//        webClient = webClientBuilder.baseUrl("https://idsm.elixir-czech.cz/sparql/endpoint/idsm").build();
//        webClient = webClientBuilder.baseUrl("localhost:7896").build();
//        webClient = webClientBuilder.build();

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create().followRedirect(true)
                ))
                .build();
    }

    @PostMapping("/java")
    public void forwardRequestJava(@RequestHeader Map<String, String> headerMap, @RequestBody String body, String uri) {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://idsm.elixir-czech.cz/sparql/endpoint/idsm"))
                .POST(HttpRequest.BodyPublishers.ofString(body));

        headerMap.entrySet().stream()
                .filter(entry -> !(entry.getKey().equalsIgnoreCase("host") || entry.getKey().equalsIgnoreCase("content-length")))
                .forEach(entry -> requestBuilder.header(entry.getKey(), entry.getValue()));

        HttpRequest request = requestBuilder.build();


        HttpClient client = HttpClient.newHttpClient();

        CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        response.thenApply(HttpResponse::body)
                .thenAccept(respBody -> {
                    Path tempFile;
                    try {
                        tempFile = Files.createTempFile("response", ".tmp");
                        Files.write(tempFile, respBody.getBytes(),
                                StandardOpenOption.CREATE);
                    } catch (IOException e) {
                        // Handle or log the IOException
                        throw new RuntimeException(e);
                    }
                })
                .exceptionally(e -> {
                    System.err.println("Error during request: " + e.getMessage());
                    return null;
                });
//                .join(); // Blocks to wait for the response, not ideal in real production code


    }

    @PostMapping("/")
    public Mono<String> forwardRequest(@RequestHeader Map<String, String> headerMap, @RequestBody String body, String uri) {
        if(uri == null) {
            uri = "https://idsm.elixir-czech.cz/sparql/endpoint/idsm";
        }
        System.out.println("Received request headers: " + headerMap);
        System.out.println("Received request body: " + body);

        /*
        Path tempFile;
        try {
            tempFile = Files.createTempFile("response", ".tmp");
        } catch (IOException e) {
            throw new RuntimeException();
        }
         */


        webClient.post()
                .uri(uri)
                .headers(hdrs -> hdrs.setAll(headerMap))
                .bodyValue(body)
                .exchangeToMono(clientResponse -> {
                    return clientResponse.bodyToMono(String.class);
/*
                    if (clientResponse.statusCode().equals(HttpStatus.FOUND)) { // 302
                        System.out.println();
                        System.out.println("MMO-recursive call");

                        String newUrl = clientResponse.headers().header("Location").get(0);

                        return forwardRequest(headerMap, body, newUrl);
                    } else {
                        return clientResponse.bodyToMono(String.class);
                    }

 */
                })
                .subscribe(resp -> {
                    System.out.println(resp);
                });



/*
        response.onStatus(status -> status.is3xxRedirection(), resp -> {
                    String redirectUrl = resp.headers().header("Location").get(0);
                    return resp.bodyToMono(Void.class).then(forwardRequest(headerMap, body, redirectUrl));

                })


                .flatMap(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        String redirectUrl = response.headers().header("Location").get(0);
                        return response.bodyToMono(Void.class).then(hello(redirectUrl));
                    }
                    return response.bodyToMono(String.class);
                }
                .bodyToMono(String.class)
                .subscribe(resp -> {
                    System.out.println(resp);

                    /*
                    try {
                        Files.write(tempFile, resp.getBytes(),
                                StandardOpenOption.CREATE);
                    } catch (IOException e) {
                        // Handle or log the IOException
                        throw new RuntimeException(e);
                    } finally {
                    }
                   */
//                });

//                .doOnSuccess(path -> System.out.println("Response saved to: " + path.toString()))
//                .doOnError(e -> System.err.println("Error: " + e.getMessage()));

/*
        webClient.post()
                .uri("/")
                .headers(hdrs -> hdrs.setAll(headerMap))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(dataBuffer -> {
                    // Save each chunk of data to file
                    try {
                        Files.write(tempFile, dataBuffer.toByteBuffer().array(),
                                StandardOpenOption.CREATE);
                    } catch (IOException e) {
                        // Handle or log the IOException
                        throw new RuntimeException(e);
                    } finally {
                        DataBufferUtils.release(dataBuffer); // Ensure releasing the DataBuffer
                    }
                })
                .then(Mono.just(tempFile))
                .doOnSuccess(path -> System.out.println("Response saved to: " + path.toString()))
                .doOnError(e -> System.err.println("Error: " + e.getMessage()));
*/
        /*
        HttpEntity<String> requestEntity = new HttpEntity<>(body);
        ResponseEntity<String> response = restTemplate.exchange(forwardUrl, HttpMethod.POST, requestEntity, String.class);
         */

        // Log the response


        return null;
    }
}