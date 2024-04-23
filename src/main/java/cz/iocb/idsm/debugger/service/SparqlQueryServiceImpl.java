package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.grammar.SparqlLexerDebug;
import cz.iocb.idsm.debugger.model.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.web.util.UrlPathHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static cz.iocb.idsm.debugger.util.DebuggerUtil.prettyPrintTree;
import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static cz.iocb.idsm.debugger.util.DebuggerUtil.unwrapIri;
import static java.lang.String.format;

@Service
public class SparqlQueryServiceImpl implements SparqlQueryService {

    private Map<Long, Tree<SparqlQueryInfo>> queryMap = new HashMap<>();

    public static final String SYS_VAR_PROXY_ENDPOINT = "proxyEndpoint";
    public static final String DEFAULT_PROXY_ENDPOINT = "localhost:8080";

    @Value("${debugService:localhost:8080/service}")
    private String debugServiceUriStr;

    private AtomicLong endpointCounter = new AtomicLong(0);
    private Map<Long, String> endpointMap = Collections.synchronizedMap(new HashMap<>());

    private static final Logger logger = LoggerFactory.getLogger(SparqlQueryServiceImpl.class);

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

            queryMap.put(queryId, resultTree);

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
            logger.debug(format("queryTree: \n%s", prettyPrintTree(queryTree, (SparqlQueryInfo queryInfo) -> queryInfo.nodeId.toString())));
            return queryTree.getRoot().findNode(queryInfo -> queryInfo.nodeId == subqueryId);
        }
    }

    @Override
    public String getEndpoint(Long endpointId) {
        String result = endpointMap.get(endpointId);
        if(result == null) {
            throw new SparqlDebugException(format("Non registered endpoint. EndpointId=%d", endpointId));
        }

        return result;
    }

    private Long getChildSubbqueryId(Node<SparqlQueryInfo> queryNode, Integer position) {
        return queryNode.getChildren().get(position).getData().nodeId;
    }

    private String injectUrl(String endpoint, ProxyQueryParams proxyQueryParams) {

        Long endpointId;
        synchronized (endpointMap) {
            Optional<Long> endpId = endpointMap.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(endpoint))
                    .map(entry -> entry.getKey()).findAny();
            if(endpId.isPresent()) {
                endpointId = endpId.get();
            } else {
                endpointId = endpointCounter.addAndGet(1);
                endpointMap.put(endpointId, endpoint);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(debugServiceUriStr).append("/")
                .append(PATH_QUERY_ID).append("/").append(proxyQueryParams.getQueryId()).append("/")
                .append(PATH_PARENT_CALL_ID).append("/").append(proxyQueryParams.getParentId()).append("/")
                .append(PATH_SUBQUERY_ID).append("/").append(proxyQueryParams.getSubQueryId()).append("/")
                .append(PATH_ENDPOINT).append("/").append(endpointId);

        String injectedUrl = sb.toString();

        String result = format("<%s>", injectedUrl);

        logger.debug(format("injectUrl: endpoint=%s, injectedUrl=%s", endpoint, result));

        return result;
    }
}
