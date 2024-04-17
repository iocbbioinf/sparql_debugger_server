package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.grammar.SparqlLexerDebug;
import cz.iocb.idsm.debugger.model.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cz.iocb.idsm.debugger.model.Tree.Node;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static cz.iocb.idsm.debugger.util.IriUtil.unwrapIri;
import static java.lang.String.format;

@Service
public class SparqlQueryServiceImpl implements SparqlQueryService {

    private Map<Long, Tree<SparqlQueryInfo>> queryMap = new HashMap<>();

    public static final String SYS_VAR_PROXY_ENDPOINT = "proxyEndpoint";
    public static final String DEFAULT_PROXY_ENDPOINT = "localhost:8080";

    @Value("${debugService:localhost:8080/service}")
    private String debugServiceUriStr;

    @Override
    public Tree<SparqlQueryInfo> createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException {
        try {
            SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

            Long nodeIndex = 0L;

            SparqlQueryInfo rootNode = new SparqlQueryInfo(new URI(endpoint), query, queryId, nodeIndex);
            Tree<SparqlQueryInfo> resultTree = new Tree<>(rootNode);

            Stack<QueryStackElement> nodeStack = new Stack<>();
            nodeStack.push(new QueryStackElement(resultTree.getRoot(), new StringBuilder(), 0));

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
                            SparqlQueryInfo queryNode = new SparqlQueryInfo(new URI(unwrapIri(token.getText())), null, queryId, nodeIndex);
                            Node<SparqlQueryInfo> newNode = nodeStack.peek().queryNode.addNode(queryNode);
                            newStackElement = new QueryStackElement(
                                    newNode,
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
                        if (nodeStack.peek().inServiceBodyLevel == 0 && nodeStack.peek().queryNode.getParent() != null) {
                            String body = nodeStack.peek().sb.toString();
                            nodeStack.peek().queryNode.getData().query = body.substring(0, body.length() - 1);
                            nodeStack.pop();
                        }
                    }
                }
            }

            nodeStack.peek().queryNode.getData().query = nodeStack.peek().sb.toString();


            return resultTree;

        } catch (URISyntaxException e) {
            throw new SparqlDebugException(e);
        }
    }

    private static class QueryStackElement {
        Node<SparqlQueryInfo> queryNode;
        StringBuilder sb;
        Integer inServiceBodyLevel;

        public QueryStackElement(Node<SparqlQueryInfo> queryNode, StringBuilder sb, Integer inServiceBodyLevel) {
            this.queryNode = queryNode;
            this.sb = sb;
            this.inServiceBodyLevel = inServiceBodyLevel;
        }
    }

    @Override
    public String injectOuterServices(String query, EndpointCall endpointCall) {
        SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

        Integer injectionCounter = 0;
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
                            Long subqueryId = getChildSubbqueryId(endpointCall.queryNode, injectionCounter);
                            ProxyQueryParams proxyQueryParams =
                                    new ProxyQueryParams(endpointCall.queryId, endpointCall.nodeId, subqueryId);
                            String iri = token.getText();
                            newTokenStr = injectUrl(unwrapIri(iri), proxyQueryParams);
                            injectionCounter ++;
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

    private Long getChildSubbqueryId(Node<SparqlQueryInfo> queryNode, Integer position) {
        return queryNode.getChildren().get(position).getData().nodeId;
    }

    private String injectUrl(String endpoint, ProxyQueryParams proxyQueryParams) {

        try {
            String injectedUrl = addQueryParam(new URI(debugServiceUriStr),
                    PARAM_ENDPOINT + "=" + endpoint,
                    PARAM_QUERY_ID + "=" + proxyQueryParams.getQueryId(),
                    PARAM_PARENT_CALL_ID + "=" + proxyQueryParams.getParentId(),
                    PARAM_SUBQUERY_ID + "=" + proxyQueryParams.getSubQueryId()).toString();

            return format("<%s>", injectedUrl);
        } catch (URISyntaxException e) {
            throw new SparqlDebugException("Service IRI in query isn't valid.", e);
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
