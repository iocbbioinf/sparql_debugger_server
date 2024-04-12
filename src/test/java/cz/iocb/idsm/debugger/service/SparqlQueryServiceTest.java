package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.SparqlQueryNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
public class SparqlQueryServiceTest {

    SparqlQueryService sparqlQueryService;

    @Autowired
    public SparqlQueryServiceTest(SparqlQueryService sparqlQueryService) {
        this.sparqlQueryService = sparqlQueryService;
    }

    @Test
    void sanityCheck() {

        String query = """
            PREFIX dc: <http://purl.org/dc/elements/1.1/>
            PREFIX foaf: <http://xmlns.com/foaf/0.1/>
            PREFIX org: <http://www.w3.org/ns/org#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            
            SELECT ?title ?publicationYear ?authorName ?affiliationName
            WHERE {
              # Querying the publications endpoint for publications about "Artificial Intelligence" after 2010
              SERVICE <https://sparql.nextprot.org/> {
                SERVICE <https://sparql.nextprot.org/>
                {   
                    ?publication dc:title ?title .
                }
                SERVICE <https://sparql.nextprot.org/>
                {
                    ?publication dc:date ?publicationYear .                
                }                
                FILTER(CONTAINS(LCASE(?title), "artificial intelligence"))
                FILTER(?publicationYear > 2010)
                ?publication dc:creator ?author .
              }
            
              # Querying the authors endpoint for the authors' names and affiliations
              SERVICE <https://sparql.nextprot.org/> {
                ?author foaf:name ?authorName .
                OPTIONAL {
                  ?author org:affiliation ?affiliation .
                  ?affiliation rdfs:label ?affiliationName .
                }
              }
            }
            ORDER BY ?publicationYear                                
         """;

        String testEndpoint = "http://test.org";

        SparqlQueryNode root = sparqlQueryService.createQueryTree(testEndpoint, query, 1L);

        System.out.println("MMO-tmp - root: " + root.toString());

    }
}
