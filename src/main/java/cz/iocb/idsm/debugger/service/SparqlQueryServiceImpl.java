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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static cz.iocb.idsm.debugger.util.DebuggerUtil.*;
import static cz.iocb.idsm.debugger.util.HttpUtil.*;
import static java.lang.String.format;

@Service
public class SparqlQueryServiceImpl implements SparqlQueryService {

    private final Map<Long, Tree<SparqlQueryInfo>> queryMap = new ConcurrentHashMap<>();

    @Value("${debugService:localhost:8080/service}")
    private String debugServiceUriStr;

    private final AtomicLong endpointCounter = new AtomicLong(0);
    private final Map<Long, String> endpointMap = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(SparqlQueryServiceImpl.class);

    public static final Long SUBQUERY_NOT_EXISTING_ID = -1L;

    @Override
    public Tree<SparqlQueryInfo> createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException {

        String unPrefixedQuery = substServicePrefixes(query);

        try {
            SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(unPrefixedQuery));

            Long nodeIndex = 0L;

            SparqlQueryInfo rootNode = new SparqlQueryInfo(new URI(endpoint), unPrefixedQuery, queryId, nodeIndex);
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

                    case SparqlLexerDebug.IRIREF, SparqlLexerDebug.PNAME_LN -> {
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

        String unPrefixedQuery = substServicePrefixes(query);

        SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(unPrefixedQuery));

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
                            Long subqueryId = getChildQuerySubqueryId(endpointCall.getQueryNode(), injectionCounter);
                            ProxyQueryParams proxyQueryParams =
                                    new ProxyQueryParams(endpointCall.getQueryId(), endpointCall.getNodeId(), subqueryId, injectionCounter.longValue());
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
                        inServiceBodyLevel++;
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
            logger.debug("queryTree: \n{}", prettyPrintTree(queryTree, (SparqlQueryInfo queryInfo) -> queryInfo.nodeId.toString()));
            return queryTree.getRoot().findNode(queryInfo -> queryInfo.nodeId.equals(subqueryId));
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

    private String substServicePrefixes(String query) {
        SparqlLexerDebug lexer = new SparqlLexerDebug(new ANTLRInputStream(query));

        Map<String, String> prefixMap = new ConcurrentHashMap<>();

        boolean inService = false;
        StringBuilder sb = new StringBuilder();

        boolean inPrefix = false;
        String prefixName = null;
        boolean inBase = false;

        String base = null;

        for (Token token = lexer.nextToken();
             token.getType() != Token.EOF;
             token = lexer.nextToken()) {

            String newTokenStr = token.getText();
            switch (token.getType()) {

                case SparqlLexerDebug.BASE -> {
                    inBase = true;
                }

                case SparqlLexerDebug.PREFIX -> {
                    inPrefix = true;
                }

                case SparqlLexerDebug.PNAME_NS -> {
                    if(inPrefix) {
                        String[] tokenArr = token.getText().split(":");
                        if(tokenArr.length > 0) {
                            prefixName = tokenArr[0].trim();
                        } else {
                            prefixName = "";
                        }
                    }
                }

                case SparqlLexerDebug.SERVICE -> {
                    inService = true;
                }

                case SparqlLexerDebug.PNAME_LN -> {
                    if (inService) {
                        newTokenStr = resolvePrefixedValue(token.getText(), prefixMap);
                        inService = false;
                    }
                }

                case SparqlLexerDebug.IRIREF -> {
                    if(inBase) {
                        base = token.getText();
                        inBase = false;
                    }

                    if(inPrefix) {
                        prefixMap.put(prefixName, token.getText());
                        inPrefix = false;
                    }

                    if(inService) {
                        if(base != null) {
                            newTokenStr = resolveRelativeIri(token.getText(), base);
                        }
                        inService = false;
                    }
                }
            }

            sb.append(newTokenStr);
        }

        return sb.toString();
    }

    private String resolvePrefixedValue(String prefixedValue, Map<String, String> prefixMap) {
        String[] strArr = prefixedValue.split(":");
        String prefix = "";
        String value = "";
        if(strArr.length == 1) {
            value = strArr[0].trim();
        } else {
            prefix = strArr[0].trim();
            value = strArr[1].trim();
        }

        String result = wrapIri(format("%s%s", unwrapIri(prefixMap.get(prefix)), value));

        return result;
    }

    private String resolveRelativeIri(String relIri, String baseIri) {
        String result = wrapIri(format("%s%s", unwrapIri(baseIri), unwrapIri(relIri)));
        return result;
    }

    private Long getChildQuerySubqueryId(Node<SparqlQueryInfo> queryNode, Integer position) {
        if(queryNode.getChildren().get(position) != null) {
            return queryNode.getChildren().get(position).getData().nodeId;
        } else {
            return SUBQUERY_NOT_EXISTING_ID;
        }
    }

    private String injectUrl(String endpoint, ProxyQueryParams proxyQueryParams) {

        Long endpointId;
        Optional<Long> endpId = endpointMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(endpoint))
                .map(entry -> entry.getKey()).findAny();
        if(endpId.isPresent()) {
            endpointId = endpId.get();
        } else {
            endpointId = endpointCounter.addAndGet(1);
            endpointMap.put(endpointId, endpoint);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(debugServiceUriStr).append("/")
                .append(PATH_QUERY_ID).append("/").append(proxyQueryParams.getQueryId()).append("/")
                .append(PATH_PARENT_CALL_ID).append("/").append(proxyQueryParams.getParentId()).append("/")
                .append(PATH_SUBQUERY_ID).append("/").append(proxyQueryParams.getSubQueryId()).append("/")
                .append(PATH_SERVICE_CALL_ID).append("/").append(proxyQueryParams.getServiceCallId()).append("/")
                .append(PATH_ENDPOINT).append("/").append(endpointId).append("/");


        String injectedUrl = sb.toString();

        String result = format("<%s>", injectedUrl);

        logger.debug("injectUrl: endpoint={}, injectedUrl={}", endpoint, result);

        return result;
    }

    @Override
    public void deleteQuery(Long queryId) {
        queryMap.remove(queryId);
    }
}
