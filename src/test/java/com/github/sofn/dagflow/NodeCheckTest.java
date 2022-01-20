package com.github.sofn.dagflow;

import com.github.sofn.dagflow.model.Node;
import com.github.sofn.dagflow.model.NodeCheck;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2021-11-04 12:15
 */
class NodeCheckTest {

    @Test
    @SuppressWarnings("unchecked")
    void hasCycle() {
        Node node1 = new Node<>("node1");
        Node node2 = new Node<>("node2");
        Node node3 = new Node<>("node3");
        Node node4 = new Node<>("node4");

        node1.addDepend(node2);
        node2.addDepend(node3);
        node3.addDepend(node4);
        boolean check = NodeCheck.hasCycle(Lists.newArrayList(node1, node2, node3, node4));
        assertFalse(check);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasCycle2() {
        Node node1 = new Node<>("node1");
        Node node2 = new Node<>("node2");
        Node node3 = new Node<>("node3");
        Node node4 = new Node<>("node4");


        node1.addDepend(node2);
        node1.addDepend(node3);
        node2.addDepend(node3);
        node3.addDepend(node1);
        node3.addDepend(node4);
        boolean check = NodeCheck.hasCycle(Lists.newArrayList(node1, node2, node3));
        assertTrue(check);
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenGraph_whenCycleExists_thenReturnTrue() {
        Node nodeA = new Node("A");
        Node nodeB = new Node("B");
        Node nodeC = new Node("C");
        Node nodeD = new Node("D");

        nodeA.addDepend(nodeB);
        nodeB.addDepend(nodeC);
        nodeC.addDepend(nodeA);
        nodeD.addDepend(nodeC);

        assertTrue(NodeCheck.hasCycle(Lists.newArrayList(nodeA, nodeB, nodeC, nodeD)));

    }
}