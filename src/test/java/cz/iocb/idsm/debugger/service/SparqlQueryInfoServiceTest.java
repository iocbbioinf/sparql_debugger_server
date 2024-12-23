package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.SparqlQueryInfo;
import cz.iocb.idsm.debugger.model.Tree;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
public class SparqlQueryInfoServiceTest {

    SparqlQueryService sparqlQueryService;

    @Autowired
    public SparqlQueryInfoServiceTest(SparqlQueryService sparqlQueryService) {
        this.sparqlQueryService = sparqlQueryService;
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("debugService", () -> "http://localhost:8080:");
    }

    @Test
    void sanityCheck() {

        String query = """
            PREFIX dc: <http://purl.org/dc/elements/1.1/>
            PREFIX : <http://xmlns.com/foaf/0.1/>
            PREFIX org: <http://www.w3.org/ns/org#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            BASE <http://test.base.org/>
            
            SELECT ?title ?publicationYear ?authorName ?affiliationName
            WHERE {
              # Querying the publications endpoint for publications about "Artificial Intelligence" after 2010
              SERVICE <service1> {
                SERVICE dc:service2
                {   
                    ?publication dc:title ?title .
                }
                SERVICE <service3>
                {
                    ?publication dc:date ?publicationYear .                
                }                
                FILTER(CONTAINS(LCASE(?title), "artificial intelligence"))
                FILTER(?publicationYear > 2010)
                ?publication dc:creator ?author .
              }
            
              # Querying the authors endpoint for the authors' names and affiliations
              SERVICE :service4 {
                ?author :name ?authorName .
                OPTIONAL {
                  ?author org:affiliation ?affiliation .
                  ?affiliation rdfs:label ?affiliationName .
                }
              }
            }
            ORDER BY ?publicationYear                                
         """;

        String testEndpoint = "http://test.org";

        Tree<SparqlQueryInfo> root = sparqlQueryService.createQueryTree(testEndpoint, query, 1L);

    }
}
