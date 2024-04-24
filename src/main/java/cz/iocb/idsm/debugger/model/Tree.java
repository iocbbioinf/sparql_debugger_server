package cz.iocb.idsm.debugger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class Tree<T> {
    private Node<T> root;

    public Tree(T rootData) {
        root = new Node<T>(rootData, null);
        root.data = rootData;
        root.children = new ArrayList<Node<T>>();
    }

    public Node<T> getRoot() {
        return root;
    }

    public static class Node<T> {
        private T data;
        private Node<T> parent;
        private List<Node<T>> children;

        private Node(T data, Node<T> parent) {
            this.data = data;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        public Node<T> addNode(T data) {
            Node<T> node = new Node<T>(data, this);
            this.children.add(node);

            return node;
        }

        public Optional<Node<T>> findNode(Predicate<T> predicate) {
            return findNode(this, predicate);
        }

        public Optional<Node<T>> findNode(Node<T> node, Predicate<T> predicate) {
            if(predicate.test(node.data)) {
                return Optional.of(node);
            } else {
                for(Node child : node.children) {
                    Optional<Node<T>> result = findNode(child, predicate);
                    if(result.isPresent()) {
                        return result;
                    }
                }
                return Optional.empty();
            }
        }

        public T getData() {
            return data;
        }

        @JsonIgnore
        public Node<T> getParent() {
            return parent;
        }

        public List<Node<T>> getChildren() {
            return children;
        }
    }
}