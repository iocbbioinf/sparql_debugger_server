package cz.iocb.idsm.debugger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@RestController
public class ForwardingController {

    private final RestTemplate restTemplate;
    private final WebClient webClient;

    @Autowired
    public ForwardingController(RestTemplate restTemplate,  WebClient.Builder webClientBuilder) {
        this.restTemplate = restTemplate;
        webClient = webClientBuilder.baseUrl("https://sparql.nextprot.org").build();
//        webClient = webClientBuilder.baseUrl("localhost:7896").build();
    }

    @PostMapping("/")
    public Mono<String> forwardRequest(@RequestHeader Map<String, String> headerMap, @RequestBody String body) {
        System.out.println("Received request headers: " + headerMap);
        System.out.println("Received request body: " + body);

        Path tempFile;
        try {
            tempFile = Files.createTempFile("response", ".tmp");
        } catch (IOException e) {
            throw new RuntimeException();
        }

        Mono<String> result = webClient.post()
                .uri("/")
                .headers(hdrs -> hdrs.setAll(headerMap))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                /*
                .subscribe(resp -> {
                    // Save each chunk of data to file
                    try {
                        Files.write(tempFile, resp.getBytes(),
                                StandardOpenOption.CREATE);
                    } catch (IOException e) {
                        // Handle or log the IOException
                        throw new RuntimeException(e);
                    } finally {
                    }
                })

                 */
                .doOnSuccess(path -> System.out.println("Response saved to: " + path.toString()))
                .doOnError(e -> System.err.println("Error: " + e.getMessage()));

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

        // Return the same body for simplicity
        return result;
    }
}