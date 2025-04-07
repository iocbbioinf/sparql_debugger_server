package cz.iocb.idsm.debugger.service;

import cz.iocb.idsm.debugger.model.*;
import cz.iocb.idsm.debugger.model.Tree.Node;

import java.util.Optional;

/**
 * Interface for creating and managing SPARQL query trees.
 */

public interface SparqlQueryService {

    /**
     * Creates a tree representing the SPARQL query and its subqueries.
     *
     * @param endpoint the endpoint URL.
     * @param query the SPARQL query.
     * @param queryId a unique identifier for the query.
     * @return a Tree of SparqlQueryInfo objects.
     * @throws SparqlDebugException if query parsing fails.
     */
    Tree<SparqlQueryInfo> createQueryTree(String endpoint, String query, Long queryId) throws SparqlDebugException;

    /**
     * Injects outer service URLs into the SPARQL query.
     *
     * @param query the original SPARQL query.
     * @param endpointCall the endpoint call context.
     * @return the modified SPARQL query.
     */
    String injectOuterServices(String query, EndpointCall endpointCall);

    /**
     * Retrieves a specific query node info from the query tree.
     *
     * @param queryId the query identifier.
     * @param subqueryId the subquery identifier.
     * @return an Optional containing the query node if found.
     */
    Optional<Node<SparqlQueryInfo>> getQueryInfoNode(Long queryId, Long subqueryId);

    /**
     * Deletes a query tree.
     *
     * @param queryId the identifier of the query to delete.
     */
    void deleteQuery(Long queryId);

    /**
     * Retrieves the endpoint URL associated with a given endpoint ID.
     *
     * @param endpointId the endpoint identifier.
     * @return the endpoint URL.
     */
    String getEndpoint(Long endpointId);


}
