package cz.iocb.idsm.debugger;

import cz.iocb.idsm.debugger.controller.RequestLoggingFilter;
import cz.iocb.idsm.debugger.controller.ResponseHeaderLoggingFilter;
import cz.iocb.idsm.debugger.model.SparqlRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
		return HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.build();
	}

	/*
	@Bean
	public RequestLoggingFilter requestLoggingFilter() {
		return new RequestLoggingFilter();
	}
	*/

	@Bean
	public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
		FilterRegistrationBean<RequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new RequestLoggingFilter());
		registrationBean.addUrlPatterns("/query/*", "/service/*"); // Restrict to specific paths
		registrationBean.setName("RequestLoggingFilter");
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<ResponseHeaderLoggingFilter> responseLoggingFilter() {
		FilterRegistrationBean<ResponseHeaderLoggingFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new ResponseHeaderLoggingFilter());
		registrationBean.addUrlPatterns("/query/*", "/service/*"); // Restrict to specific paths
		registrationBean.setName("ResponseLoggingFilter");
		return registrationBean;
	}

	@Bean
	@RequestScope
	public SparqlRequest sparqlRequestBean() {
		return new SparqlRequest();
	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {

		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry
						.addMapping("/**")
						.allowedMethods(CorsConfiguration.ALL)
						.allowedHeaders(CorsConfiguration.ALL)
						.allowedOriginPatterns(CorsConfiguration.ALL);
			}
		};
	}

}
