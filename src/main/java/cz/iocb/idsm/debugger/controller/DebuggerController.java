package cz.iocb.idsm.debugger.controller;

import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import cz.iocb.idsm.debugger.service.SparqlQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DebuggerController {

    @Autowired
    private SparqlQueryService sparqlQueryService;

    @Autowired
    private SparqlEndpointService sparqlEndpointService;

    @PostMapping("/service")
    public void debugService(@RequestHeader Map<String, String> headerMap, @RequestBody String body) {
    }

    @PostMapping("/query")
    public void debugQuery(@RequestHeader Map<String, String> headerMap, @RequestBody String body) {
        sparqlQueryService.c

    }

}
