package cz.iocb.idsm.debugger.model;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.*;

@Component
@SessionScope
public class SessionScopeList {

    private final List<Long> sessionList = Collections.synchronizedList(new ArrayList<>());

    public int size() {
        return sessionList.size();
    }

    public boolean isEmpty() {
        return sessionList.isEmpty();
    }

    public boolean contains(Object o) {
        return sessionList.contains(o);
    }

    public Iterator<Long> iterator() {
        return sessionList.iterator();
    }

    public boolean add(Long aLong) {
        return sessionList.add(aLong);
    }

    public boolean remove(Object o) {
        return sessionList.remove(o);
    }

    public void clear() {
        sessionList.clear();
    }

    public Long get(int index) {
        return sessionList.get(index);
    }

    public Long set(int index, Long element) {
        return sessionList.set(index, element);
    }

    public void add(int index, Long element) {
        sessionList.add(index, element);
    }

    public Long remove(int index) {
        return sessionList.remove(index);
    }

    public int indexOf(Object o) {
        return sessionList.indexOf(o);
    }
}
