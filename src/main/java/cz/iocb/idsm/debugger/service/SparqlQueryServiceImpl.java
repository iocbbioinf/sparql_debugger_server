package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.grammar.SparqlLexerDebug;
import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlDebugException;
import cz.iocb.idsm.debugger.model.SparqlQueryNode;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static cz.iocb.idsm.debugger.util.IriUtil.unwrapIri;

@Service
public class SparqlQueryServiceImpl implements SparqlQueryService {

    private Map<Long, SparqlQueryNode> queryMap = new HashMap<>();

    public static final String SYS_VAR_PROXY_ENDPOINT = "proxyEndpoint";
    public static final String DEFAULT_PROXY_ENDPOINT = "localhost:8080";

    public static final String PARAM_NODE_ID = "dNodeId";


    @Override
    public SparqlQueryNode createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException {
        try {
            SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

            int nodeIndex = 0;

            SparqlQueryNode rootNode = new SparqlQueryNode(new URL(endpoint), query, queryId, nodeIndex, null);
            Stack<QueryStackElement> nodeStack = new Stack<>();
            nodeStack.push(new QueryStackElement(rootNode, new StringBuilder(), 0));

            boolean inService = false;
            QueryStackElement newStackElement = null;
            for (Token token = lexer.nextToken();
                 token.getType() != Token.EOF;
                 token = lexer.nextToken()) {

                String newTokenStr = token.getText();

                nodeStack.stream().forEach(stackElement -> stackElement.sb.append(newTokenStr));

                switch (token.getType()) {
                    case SparqlLexerDebug.SERVICE -> {
                        inService = true;
                    }

                    case SparqlLexerDebug.IRIREF -> {
                        if (inService) {
                            nodeIndex++;
                            SparqlQueryNode queryNode = new SparqlQueryNode(new URL(unwrapIri(token.getText())), null, queryId, nodeIndex, nodeStack.peek().queryNode);
                            nodeStack.peek().queryNode.children.add(queryNode);
                            newStackElement = new QueryStackElement(
                                    queryNode,
                                    new StringBuilder(), 0);
                        }
                    }

                    case SparqlLexerDebug.OPEN_CURLY_BRACE -> {
                        if (inService) {
                            newStackElement.inServiceBodyLevel++;
                            nodeStack.push(newStackElement);
                            inService = false;
                        } else {
                            nodeStack.peek().inServiceBodyLevel++;
                        }
                    }

                    case SparqlLexerDebug.CLOSE_CURLY_BRACE -> {
                        nodeStack.peek().inServiceBodyLevel--;
                        if (nodeStack.peek().inServiceBodyLevel == 0 && nodeStack.peek().queryNode.parentNode != null) {
                            String body = nodeStack.peek().sb.toString();
                            nodeStack.peek().queryNode.query = body.substring(0, body.length() - 1);
                            nodeStack.pop();
                        }
                    }
                }
            }

            nodeStack.peek().queryNode.query = nodeStack.peek().sb.toString();


            return rootNode;

        } catch (MalformedURLException e) {
            throw new SparqlDebugException(e);
        }
    }

    private static class QueryStackElement {
        SparqlQueryNode queryNode;
        StringBuilder sb;
        Integer inServiceBodyLevel;

        public QueryStackElement(SparqlQueryNode queryNode, StringBuilder sb, Integer inServiceBodyLevel) {
            this.queryNode = queryNode;
            this.sb = sb;
            this.inServiceBodyLevel = inServiceBodyLevel;
        }
    }

    @Override
    public String injectOuterServices(String endpoint, String query, ProxyQueryParams proxyQueryParams) {
        SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

        boolean inService = false;
        int inServiceBodyLevel = 0;
        StringBuilder sb = new StringBuilder();
        for (Token token = lexer.nextToken();
             token.getType() != Token.EOF;
             token = lexer.nextToken()) {

            String newTokenStr = token.getText();
            if (inServiceBodyLevel == 0) {
                switch (token.getType()) {
                    case SparqlLexerDebug.SERVICE -> {
                        inService = true;
                    }
                    case SparqlLexerDebug.IRIREF -> {
                        if (inService) {
                            String iri = token.getText();

                            newTokenStr = injectUrl(iri, null);
                        }
                    }
                }
            }
            if (inService) {
                switch (token.getType()) {
                    case SparqlLexerDebug.CLOSE_CURLY_BRACE -> {
                        inServiceBodyLevel--;
                        if (inServiceBodyLevel == 0) {
                            inService = false;
                        }
                    }
                    case SparqlLexerDebug.OPEN_CURLY_BRACE -> {
                        if (inService) {
                            inServiceBodyLevel++;
                        }
                    }
                }
            }
            sb.append(newTokenStr);
        }

        return sb.toString();
    }


    private String injectUrl(String iri, SparqlQueryNode queryNode) {

        String unwrappedIri = unwrapIri(iri);

        addQueryParam(proxyEndpoint, List.of(PARAM_NODE_ID + "=" + queryNode.nodeId.toString()));

    }

    private URI addQueryParam(URI uri, String... queryParams) throws URISyntaxException {

        String newQuery = uri.getQuery();

        for(String queryParam: queryParams) {
            if (newQuery == null) {
                newQuery = queryParam;
            } else {
                newQuery += "&" + queryParam;
            }
        }

        return new URI(uri.getScheme(), uri.getAuthority(),
                uri.getPath(), newQuery, uri.getFragment());
    }

}
