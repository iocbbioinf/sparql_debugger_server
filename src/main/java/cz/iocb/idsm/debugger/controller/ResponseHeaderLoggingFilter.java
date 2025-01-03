package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.service.SparqlQueryServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

public class ResponseHeaderLoggingFilter extends HttpFilter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseHeaderLoggingFilter.class);

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        chain.doFilter(request, response);

        // Log the response headers
        logResponseHeaders(response);
    }

    private void logResponseHeaders(HttpServletResponse response) {
        logger.info("RESPONSE:");
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            String headerValue = response.getHeader(headerName);
            System.out.println(headerName + ": " + headerValue);
        }
    }

}