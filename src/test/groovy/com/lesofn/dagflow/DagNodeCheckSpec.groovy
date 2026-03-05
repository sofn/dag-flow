package com.lesofn.dagflow

import com.lesofn.dagflow.model.DagNode
import com.lesofn.dagflow.model.DagNodeCheck
import spock.lang.Specification

/**
 * DagNodeCheck 环检测单元测试（迁移自 DagDagNodeCheckTest）
 */
class DagNodeCheckSpec extends Specification {

    // 线性依赖链不存在环
    // node1 → node2 → node3 → node4
    def "linear chain has no cycle"() {
        given: "4 nodes forming a linear chain: node1 -> node2 -> node3 -> node4"
        def node1 = new DagNode<>("node1")
        def node2 = new DagNode<>("node2")
        def node3 = new DagNode<>("node3")
        def node4 = new DagNode<>("node4")
        node1.addDepend(node2)
        node2.addDepend(node3)
        node3.addDepend(node4)

        expect:
        !DagNodeCheck.hasCycle([node1, node2, node3, node4])
    }

    // 菱形依赖不存在环
    //   A
    //  / \
    // B   C
    //  \ /
    //   D
    def "diamond dependency has no cycle"() {
        given: "Diamond structure: A -> B, A -> C, B -> D, C -> D"
        def nodeA = new DagNode<>("A")
        def nodeB = new DagNode<>("B")
        def nodeC = new DagNode<>("C")
        def nodeD = new DagNode<>("D")
        nodeA.addDepend(nodeB)
        nodeA.addDepend(nodeC)
        nodeB.addDepend(nodeD)
        nodeC.addDepend(nodeD)

        expect:
        !DagNodeCheck.hasCycle([nodeA, nodeB, nodeC, nodeD])
    }

    // 存在环时应检测到 - 场景1
    // node1 → node2 → node3 → node1 (cycle)
    //                  ↓
    //                 node4
    def "detect cycle scenario 1"() {
        given: "node1 -> node2 -> node3 -> node1 forms a cycle"
        def node1 = new DagNode<>("node1")
        def node2 = new DagNode<>("node2")
        def node3 = new DagNode<>("node3")
        def node4 = new DagNode<>("node4")
        node1.addDepend(node2)
        node1.addDepend(node3)
        node2.addDepend(node3)
        node3.addDepend(node1)
        node3.addDepend(node4)

        expect:
        DagNodeCheck.hasCycle([node1, node2, node3])
    }

    // 存在环时应检测到 - 场景2: A→B→C→A
    // A → B → C → A (cycle)
    // D → C
    def "detect cycle scenario 2"() {
        given: "A -> B -> C -> A forms a cycle"
        def nodeA = new DagNode<>("A")
        def nodeB = new DagNode<>("B")
        def nodeC = new DagNode<>("C")
        def nodeD = new DagNode<>("D")
        nodeA.addDepend(nodeB)
        nodeB.addDepend(nodeC)
        nodeC.addDepend(nodeA)
        nodeD.addDepend(nodeC)

        expect:
        DagNodeCheck.hasCycle([nodeA, nodeB, nodeC, nodeD])
    }

    // 单节点无环
    def "single node has no cycle"() {
        given:
        def node = new DagNode<>("single")

        expect:
        !DagNodeCheck.hasCycle([node])
    }

    // 单节点自环
    def "self loop is detected as cycle"() {
        given:
        def node = new DagNode<>("self")
        node.addDepend(node)

        expect:
        DagNodeCheck.hasCycle([node])
    }

    // 空节点列表无环
    def "empty node list has no cycle"() {
        expect:
        !DagNodeCheck.hasCycle([])
    }

    // 两节点互相依赖形成环
    // n1 ⇄ n2
    def "mutual dependency is detected as cycle"() {
        given:
        def node1 = new DagNode<>("n1")
        def node2 = new DagNode<>("n2")
        node1.addDepend(node2)
        node2.addDepend(node1)

        expect:
        DagNodeCheck.hasCycle([node1, node2])
    }

    // 复杂DAG无环 - 多层扇入扇出
    //     A
    //   / | \
    //  B  C  D
    //  \ /   |
    //   E    |
    //    \  /
    //     F
    def "complex fan-in fan-out DAG has no cycle"() {
        given: """
            Multi-layer fan-in fan-out structure:
            A -> B, A -> C, A -> D
            B -> E, C -> E, D -> F
            E -> F
        """
        def a = new DagNode<>("A")
        def b = new DagNode<>("B")
        def c = new DagNode<>("C")
        def d = new DagNode<>("D")
        def e = new DagNode<>("E")
        def f = new DagNode<>("F")
        a.addDepend(b)
        a.addDepend(c)
        a.addDepend(d)
        b.addDepend(e)
        c.addDepend(e)
        d.addDepend(f)
        e.addDepend(f)

        expect:
        !DagNodeCheck.hasCycle([a, b, c, d, e, f])
    }
}
