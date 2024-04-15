package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.grammar.SparqlLexerDebug;
import cz.iocb.idsm.debugger.model.ProxyQueryParams;
import cz.iocb.idsm.debugger.model.SparqlDebugException;
import cz.iocb.idsm.debugger.model.SparqlQueryInfo;
import cz.iocb.idsm.debugger.model.Tree;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cz.iocb.idsm.debugger.model.Tree.Node;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static cz.iocb.idsm.debugger.util.IriUtil.unwrapIri;

@Service
public class SparqlQueryServiceImpl implements SparqlQueryService {

    private Map<Long, Tree<SparqlQueryInfo>> queryMap = new HashMap<>();

    public static final String SYS_VAR_PROXY_ENDPOINT = "proxyEndpoint";
    public static final String DEFAULT_PROXY_ENDPOINT = "localhost:8080";

    @Value("${debugService}")
    private String debugServiceUriStr;

    public static final String PARAM_NODE_ID = "dNodeId";
    @Override
    public SparqlQueryInfo createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException {
        try {
            SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

            Long nodeIndex = 0L;

            SparqlQueryInfo rootNode = new SparqlQueryInfo(new URI(endpoint), query, queryId, nodeIndex, null);
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
                            SparqlQueryInfo queryNode = new SparqlQueryInfo(new URI(unwrapIri(token.getText())), null, queryId, nodeIndex, nodeStack.peek().queryNode);
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

        } catch (URISyntaxException e) {
            throw new SparqlDebugException(e);
        }
    }

    private static class QueryStackElement {
        SparqlQueryInfo queryNode;
        StringBuilder sb;
        Integer inServiceBodyLevel;

        public QueryStackElement(SparqlQueryInfo queryNode, StringBuilder sb, Integer inServiceBodyLevel) {
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

    @Override
    public Optional<Node<SparqlQueryInfo>> getQueryInfoNode(Long queryId, Long subqueryId) {
        Tree<SparqlQueryInfo> queryTree = queryMap.get(queryId);
        if(queryTree == null) {
            return Optional.empty();
        } else {
            return queryTree.getRoot().findNode(queryInfo -> queryInfo.nodeId == subqueryId);
        }

    }


    private String injectUrl(SparqlQueryInfo queryNode) {

        try {
            return addQueryParam(new URI(debugServiceUriStr),
                    PARAM_NODE_ID + "=" + queryNode.nodeId.toString()).toString();
        } catch (URISyntaxException e) {
            throw new SparqlDebugException("debugService sys. variable hasn't valid URI value.", e);
        }

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
