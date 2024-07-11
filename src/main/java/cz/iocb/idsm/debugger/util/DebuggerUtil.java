package cz.iocb.idsm.debugger.util;

import cz.iocb.idsm.debugger.model.Tree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class DebuggerUtil {

    public static String unwrapIri(String iri) {
        if (iri.startsWith("<") && iri.endsWith(">")) {
            return iri.substring(1, iri.length() - 1);
        } else {
            return iri;
        }
    }

    public static String wrapIri(String iri) {
        if (iri.startsWith("<") && iri.endsWith(">")) {
            return iri;
        } else {
            return "<" + iri + ">";
        }
    }

    public static <T> String prettyPrintTreeNode(Tree.Node node, Function<T, String> printData) {
        return prettyPrintTreeNode(node, "", true, printData, new StringBuilder());
    }

    public static <T> String prettyPrintTree(Tree tree, Function<T, String> printData) {
        return prettyPrintTreeNode(tree.getRoot(), printData);
    }

    private static <T> String prettyPrintTreeNode(Tree.Node<T> node, String prefix, boolean isTail, Function<T, String> printData, StringBuilder sb) {
        if (node != null) {
            sb.append(prefix + (isTail ? "└── " : "├── ") + printData.apply(node.getData()) + "\n");
            for (int i = 0; i < node.getChildren().size() - 1; i++) {
                prettyPrintTreeNode(node.getChildren().get(i), prefix + (isTail ? "    " : "│   "), false, printData, sb);
            }
            if (node.getChildren().size() > 0) {
                prettyPrintTreeNode(node.getChildren().get(node.getChildren().size() - 1), prefix + (isTail ? "    " : "│   "), true, printData, sb);
            }
        }
        return sb.toString();
    }

    public static <T> Map<String, T> pickFirstEntryIgnoreCase(Map<String, T> inputMap) {
        Map<String, T> result = new HashMap<>();
        Set<String> lowerCaseKeySet = new HashSet<>();

        for (Map.Entry<String, T> entry : inputMap.entrySet()) {
            String key = entry.getKey();
            String lowerCaseKey = key.toLowerCase();

            if (!lowerCaseKeySet.contains(lowerCaseKey)) {
                lowerCaseKeySet.add(lowerCaseKey);
                result.put(key, entry.getValue());
            }
        }

        return result;
    }



}
