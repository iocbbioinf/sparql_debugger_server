package cz.iocb.idsm.debugger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Tree<T> {
    private Node<T> root;
    private SseEmitter emitter;
    private Consumer<Node<T>> addNodeHandler;

    public Tree(T rootData, Consumer<Node<T>> addNodeHandler) {
        root = new Node<T>(rootData, null, this);
        root.data = rootData;
        root.children = new ArrayList<Node<T>>();

        emitter = new SseEmitter(Long.MAX_VALUE);

        this.addNodeHandler = addNodeHandler;

        // TODO
        emitter.onCompletion(() -> {
        });
        emitter.onTimeout(() -> {});

        if(addNodeHandler != null) {
            addNodeHandler.accept(root);
        }
    }

    public Tree(T rootData) {
        this(rootData, null);
    }

    public Node<T> getRoot() {
        return root;
    }

    public static class Node<T> {
        private T data;
        private Node<T> parent;
        private List<Node<T>> children;
        private Tree<T> tree;

        private Node(T data, Node<T> parent, Tree<T> tree) {
            this.data = data;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.tree = tree;
        }

        public Node<T> addNode(T data) {
            Node<T> node = new Node<T>(data, this, this.tree);
            this.children.add(node);

            if(tree.addNodeHandler != null) {
                tree.addNodeHandler.accept(node);
            }

            return node;
        }

        public Node<T> updateNode() {
            if(tree.addNodeHandler != null) {
                tree.addNodeHandler.accept(this);
            }

            return this;
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

        public Tree<T> getTree() {
            return tree;
        }
    }

    public SseEmitter getEmitter() {
        return emitter;
    }



    public void closeEmmiter() {

    }
}