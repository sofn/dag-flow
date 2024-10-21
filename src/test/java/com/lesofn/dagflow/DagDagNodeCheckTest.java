package com.lesofn.dagflow;

import com.google.common.collect.Lists;
import com.lesofn.dagflow.model.DagNode;
import com.lesofn.dagflow.model.DagNodeCheck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2021-11-04 12:15
 */
class DagDagNodeCheckTest {

    @Test
    @SuppressWarnings("unchecked")
    void hasCycle() {
        DagNode node1 = new DagNode<>("node1");
        DagNode node2 = new DagNode<>("node2");
        DagNode node3 = new DagNode<>("node3");
        DagNode node4 = new DagNode<>("node4");

        node1.addDepend(node2);
        node2.addDepend(node3);
        node3.addDepend(node4);
        boolean check = DagNodeCheck.hasCycle(Lists.newArrayList(node1, node2, node3, node4));
        assertFalse(check);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasCycle2() {
        DagNode node1 = new DagNode<>("node1");
        DagNode node2 = new DagNode<>("node2");
        DagNode node3 = new DagNode<>("node3");
        DagNode node4 = new DagNode<>("node4");


        node1.addDepend(node2);
        node1.addDepend(node3);
        node2.addDepend(node3);
        node3.addDepend(node1);
        node3.addDepend(node4);
        boolean check = DagNodeCheck.hasCycle(Lists.newArrayList(node1, node2, node3));
        assertTrue(check);
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenGraph_whenCycleExists_thenReturnTrue() {
        DagNode nodeA = new DagNode("A");
        DagNode nodeB = new DagNode("B");
        DagNode nodeC = new DagNode("C");
        DagNode nodeD = new DagNode("D");

        nodeA.addDepend(nodeB);
        nodeB.addDepend(nodeC);
        nodeC.addDepend(nodeA);
        nodeD.addDepend(nodeC);

        assertTrue(DagNodeCheck.hasCycle(Lists.newArrayList(nodeA, nodeB, nodeC, nodeD)));

    }
}