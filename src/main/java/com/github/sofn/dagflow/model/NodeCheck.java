package com.github.sofn.dagflow.model;

import com.github.sofn.dagflow.api.context.DagFlowContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author sofn
 * @version 1.0 Created at: 2021-11-04 12:05
 */
@Slf4j
@RequiredArgsConstructor
public class NodeCheck {
    private final Node<?, ?> node;
    @Setter
    @Getter
    private boolean beingVisited;
    @Setter
    @Getter
    private boolean visited;

    public static <C extends DagFlowContext> boolean hasCycle(Collection<Node<C, ?>> nodes) {
        Map<? extends Node<?, ?>, NodeCheck> nodeCheckMap = nodes.stream().collect(Collectors.toMap(it -> it, NodeCheck::new));

        for (NodeCheck vertex : nodeCheckMap.values()) {
            List<String> way = new ArrayList<>();
            if (!vertex.isVisited() && hasCycle(vertex, nodeCheckMap, way)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycle(NodeCheck current, Map<? extends Node<?, ?>, NodeCheck> nodeCheckMap, List<String> way) {
        current.setBeingVisited(true);
        way.add(current.node.getName());

        for (Node<?, ?> node : current.node.getDepends()) {
            NodeCheck neighbor = nodeCheckMap.get(node);
            if (neighbor.isBeingVisited()) {
                way.add(neighbor.node.getName());
                log.warn("cycle: " + String.join("->", way));
                return true;
            }
            if (!neighbor.isVisited() && hasCycle(neighbor, nodeCheckMap, way)) {
                return true;
            }
        }

        current.setBeingVisited(false);
        current.setVisited(true);
        return false;
    }
}
