package cz.iocb.idsm.debugger.util;

import cz.iocb.idsm.debugger.model.Tree;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DebuggerUtilTest {


    @Test
    void prettyPrettyPrintTree() {
        Tree<Integer> tree = new Tree<>(1);
        Tree.Node<Integer> child1 = tree.getRoot().addNode(2);
        Tree.Node<Integer> child2 = tree.getRoot().addNode(3);
        Tree.Node<Integer> child3 = tree.getRoot().addNode(4);

        child1.addNode(5);
        child1.addNode(6);

        child2.addNode(7);

        child3.addNode(8);
        child3.addNode(9);

        String result = DebuggerUtil.prettyPrintTree(tree, (Integer i) -> i.toString());

        String expectedOutput = "└── 1\n" +
                "    ├── 2\n" +
                "    │   ├── 5\n" +
                "    │   └── 6\n" +
                "    ├── 3\n" +
                "    │   └── 7\n" +
                "    └── 4\n" +
                "        ├── 8\n" +
                "        └── 9\n";

        Assertions.assertEquals(expectedOutput.trim(), result.trim());

    }

}
