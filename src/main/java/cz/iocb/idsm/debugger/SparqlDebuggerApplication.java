package cz.iocb.idsm.debugger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;

@SpringBootApplication
public class SparqlDebuggerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SparqlDebuggerApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public HttpClient httpClient() {
		return HttpClient.newHttpClient();
	}
}
