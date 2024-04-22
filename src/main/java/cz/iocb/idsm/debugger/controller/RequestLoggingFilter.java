package cz.iocb.idsm.debugger.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Enumeration;

public class RequestLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        logger.info("Initializing RequestLoggingFilter");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        logRequestDetails(request);
        chain.doFilter(servletRequest, servletResponse);
    }

    private void logRequestDetails(HttpServletRequest request) {
        logger.info("Request Method: {}", request.getMethod());
        logger.info("Request URI: {}", request.getRequestURI());
        logger.info("Query String: {}", request.getQueryString());
        logger.info("Request Headers: ");
        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            logger.info("Header: {} = {}", header, request.getHeader(header));
        }
    }

    @Override
    public void destroy() {
        logger.info("Destroying RequestLoggingFilter");
    }
}