package cz.iocb.idsm.debugger.model;

import cz.iocb.idsm.debugger.service.SparqlEndpointService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.*;

@Component
@SessionScope
public class SessionScopeQueryList {

    private final List<Long> sessionQueryList = Collections.synchronizedList(new ArrayList<>());

    @Autowired
    private SparqlEndpointService endpointService;

    @PreDestroy
    public void onSessionDestroy() {
        sessionQueryList.forEach(queryId -> endpointService.deleteQuery(queryId));
    }


    public int size() {
        return sessionQueryList.size();
    }

    public boolean isEmpty() {
        return sessionQueryList.isEmpty();
    }

    public boolean contains(Object o) {
        return sessionQueryList.contains(o);
    }

    public Iterator<Long> iterator() {
        return sessionQueryList.iterator();
    }

    public boolean add(Long aLong) {
        return sessionQueryList.add(aLong);
    }

    public boolean remove(Object o) {
        return sessionQueryList.remove(o);
    }

    public void clear() {
        sessionQueryList.clear();
    }

    public Long get(int index) {
        return sessionQueryList.get(index);
    }

    public Long set(int index, Long element) {
        return sessionQueryList.set(index, element);
    }

    public void add(int index, Long element) {
        sessionQueryList.add(index, element);
    }

    public Long remove(int index) {
        return sessionQueryList.remove(index);
    }

    public int indexOf(Object o) {
        return sessionQueryList.indexOf(o);
    }
}
