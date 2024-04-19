package cz.iocb.idsm.debugger.grammar;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;

import static cz.iocb.idsm.debugger.util.DebuggerUtil.unwrapIri;
import static cz.iocb.idsm.debugger.util.DebuggerUtil.wrapIri;

public class LexerTest {
    public static void main(String[] args) throws Exception {

        String query = """
                ### NXQ_00004 ###
                # Proteins expressed in brain with IHC expression level: "high" but not expressed in testis
                select distinct ?entry where {
                  ?entry :isoform ?iso.
                  # get all expression
                  ?iso :expression ?e1.
                  # highly expressed in brain
                  ?e1 :term/:childOf cv:TS-0095;:evidence/:expressionLevel :High.
                  # not expressed in testis
                  ?iso :undetectedExpression ?e2.
                  ?e2 :term cv:TS-1030.
                  minus { ?iso :detectedExpression / :term / :childOf cv:TS-1030 }
                }""";

        String query2 = """
                PREFIX cv: <http://nextprot.org/rdf/terminology/>
                PREFIX : <http://nextprot.org/rdf#>
                select * where {
                  SERVICE <https://sparql.nextprot.org/> {
                    select distinct ?entry where {
                      ?entry :isoform ?iso.
                      # get all expression
                      SERVICE <https://sparql.nextprot.org/> {
                          # SERVICE body 
                          ?iso :expression ?e1.
                      }
                      # highly expressed in brain
                      ?e1 :term/:childOf cv:TS-0095;:evidence/:expressionLevel :High.
                      # not expressed in testis
                      ?iso :undetectedExpression ?e2.
                      ?e2 :term cv:TS-1030.
                      minus { ?iso :detectedExpression / :term / :childOf cv:TS-1030 }
                      }
                    }                                
                """;

        String query3 = """
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

        SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query3));

        boolean inService = false;
        int inServiceBodyLevel = 0;
        StringBuilder sb = new StringBuilder();
        for (Token token = lexer.nextToken();
             token.getType() != Token.EOF;
             token = lexer.nextToken()) {

            String newTokenStr = token.getText();
            if(inServiceBodyLevel == 0) {
                switch (token.getType()) {
                    case SparqlLexerDebug.SERVICE -> {
                        inService = true;
                    }
                    case SparqlLexerDebug.IRIREF -> {
                        if(inService) {
                            String iri = token.getText();
                            newTokenStr = wrapIri("http:idsm.org/dEndpoint=" + unwrapIri(iri));
                        }
                    }
                }
            }
            if(inService) {
                switch (token.getType()) {
                    case SparqlLexerDebug.CLOSE_CURLY_BRACE -> {
                        inServiceBodyLevel --;
                        if(inServiceBodyLevel == 0) {
                            inService = false;
                        }
                    }
                    case SparqlLexerDebug.OPEN_CURLY_BRACE -> {
                        if(inService) {
                            inServiceBodyLevel ++;
                        }
                    }
                }
            }
            sb.append(newTokenStr);
        }

        System.out.println(sb.toString());
    }



}
