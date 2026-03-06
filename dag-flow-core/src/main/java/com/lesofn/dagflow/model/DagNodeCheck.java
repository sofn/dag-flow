package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.context.DagFlowContext;
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
public class DagNodeCheck {
    private final DagNode<?, ?> node;
    @Setter
    @Getter
    private boolean beingVisited;
    @Setter
    @Getter
    private boolean visited;

    public static <C extends DagFlowContext> boolean hasCycle(Collection<DagNode<C, ?>> nodes) {
        Map<? extends DagNode<?, ?>, DagNodeCheck> nodeCheckMap = nodes.stream().collect(Collectors.toMap(it -> it, DagNodeCheck::new));

        for (DagNodeCheck vertex : nodeCheckMap.values()) {
            List<String> way = new ArrayList<>();
            if (!vertex.isVisited() && hasCycle(vertex, nodeCheckMap, way)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycle(DagNodeCheck current, Map<? extends DagNode<?, ?>, DagNodeCheck> nodeCheckMap, List<String> way) {
        current.setBeingVisited(true);
        way.add(current.node.getName());

        for (DagNode<?, ?> node : current.node.getDepends()) {
            DagNodeCheck neighbor = nodeCheckMap.get(node);
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
