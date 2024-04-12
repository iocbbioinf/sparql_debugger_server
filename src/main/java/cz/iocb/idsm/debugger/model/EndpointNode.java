package cz.iocb.idsm.debugger.model;

import java.util.List;

public class EndpointNode {
    Long nodeId;
    Integer seqId;
    SparqlQueryNode queryNode;
    Long startTime;
    EndpointNodeState state;
    EndpointNode parent;
    List<EndpointNode> children;
}
