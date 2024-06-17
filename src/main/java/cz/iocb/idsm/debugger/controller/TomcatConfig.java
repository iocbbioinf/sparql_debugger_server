package cz.iocb.idsm.debugger.controller;

import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http2.Http2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> {
            ProtocolHandler protocolHandler = connector.getProtocolHandler();
            if (protocolHandler instanceof AbstractHttp11Protocol<?> http11Protocol) {
                http11Protocol.setExecutor(createVirtualThreadExecutor());
                // Add the Http2Protocol
                Http2Protocol http2Protocol = new Http2Protocol();
                connector.addUpgradeProtocol(http2Protocol);
            }
        });
    }

    private ExecutorService createVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
