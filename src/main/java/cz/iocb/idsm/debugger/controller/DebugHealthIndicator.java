package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlEndpointServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("debug")
public class DebugHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DebugHealthIndicator.class);

    @Override
    public Health health() {
        boolean everythingOk = checkSomething();


        if (everythingOk) {

            Health result = Health.up()
                    .withDetail("Number of canceled virtual threads", "" + SparqlEndpointServiceImpl.cancelCounter)
                    .build();

            return result;
        } else {
            return Health.down()
                    .build();
        }
    }

    private boolean checkSomething() {
        return true;
    }

}
