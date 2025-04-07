package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.model.Tree.Node;
import org.springframework.core.io.FileSystemResource;


import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for handling SPARQL Query Execution Tree
 */
public interface SparqlEndpointService {

    /**
     * Creates a service endpoint node.
     *
     * @param endpoint the endpoint URL.
     * @param queryNode the query node associated with the endpoint.
     * @param parentNode the parent endpoint node.
     * @param serviceCallId the service call identifier.
     * @return the created service endpoint node.
     */
    Node<EndpointCall>  createServiceEndpointNode(String endpoint, Node<SparqlQueryInfo> queryNode, Node<EndpointCall> parentNode, Long serviceCallId);

    /**
     * Creates the root node for a Query Execution Tree.
     *
     * @param endpoint the endpoint URI.
     * @return the root node of the query endpoint.
     */
    Node<EndpointCall> createQueryEndpointRoot(URI endpoint);

    /**
     * Prepares an HTTP request for calling an endpoint.
     *
     * @param endpoint the endpoint URI.
     * @param queryId the query identifier.
     * @param endpointCallNode the endpoint call node.
     * @return the prepared HttpRequest.
     */
    HttpRequest prepareEndpointToCall(URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);

    /**
     * Calls the endpoint synchronously.
     *
     * @param request the prepared HttpRequest.
     * @param endpoint the endpoint URI.
     * @param queryId the query identifier.
     * @param endpointCallNode the endpoint call node.
     * @return a DebugResponse containing the response details.
     */
    DebugResponse callEndpointSync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);

    /**
     * Calls the endpoint asynchronously.
     *
     * @param request the prepared HttpRequest.
     * @param endpoint the endpoint URI.
     * @param queryId the query identifier.
     * @param endpointCallNode the endpoint call node.
     */
    void callEndpointAsync(HttpRequest request, URI endpoint, Long queryId, Node<EndpointCall> endpointCallNode);

    /**
     * Retrieves a specific endpoint node.
     *
     * @param queryId the query identifier.
     * @param nodeId the node identifier.
     * @return an Optional containing the endpoint node if found.
     */
    Optional<Node<EndpointCall>> getEndpointNode(Long queryId, Long nodeId);

    /**
     * Retrieves the query execution tree for a given query.
     *
     * @param queryId the query identifier.
     * @return an Optional containing the query tree if found.
     */
    Optional<Tree<EndpointCall>> getQueryTree(Long queryId);

    /**
     * Retrieves the file resource corresponding to a given FileId.
     *
     * @param fileId the FileId of the request/response file.
     * @return the FileSystemResource.
     */
    FileSystemResource getFile(FileId fileId);

    /**
     * Determines the number of results in the response.
     *
     * @param resultStream the InputStream of the response.
     * @param resultType the type of result.
     * @param contentEncoding the list of content encoding headers.
     * @return the number of results.
     */
    Long getResultCount(InputStream resultStream, SparqlResultType resultType, List<String> contentEncoding);

    /**
     * Deletes a query from the service.
     *
     * @param queryId the query identifier.
     */
    void deleteQuery(Long queryId);

    /**
     * Deletes queries older than a specified cutoff.
     *
     * @param cutoff the cutoff timestamp in milliseconds.
     */
    void deleteQueries(Long cutoff);

    /**
     * Retrieves the set of query IDs that are currently being cancelled.
     *
     * @return a Set of query IDs.
     */
    Set<Long> getCancellingQuerySet();

}

