package com.lesofn.dagflow.model;

import com.lesofn.dagflow.api.context.DagFlowContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
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

    /**
     * 增量环检测：检查从 newNode 出发沿依赖边是否能回到自身。
     * 仅在新增节点后调用，返回检测到的环路径（空表示无环）。
     *
     * @param newNode 新增的节点
     * @param allNodes 全部已注册节点
     * @return 如果存在环，返回环的路径字符串列表；否则返回空列表
     */
    public static <C extends DagFlowContext> List<String> checkCycleOnAdd(DagNode<C, ?> newNode, Collection<DagNode<C, ?>> allNodes) {
        Map<DagNode<?, ?>, DagNodeCheck> nodeCheckMap = new HashMap<>();
        for (DagNode<C, ?> n : allNodes) {
            nodeCheckMap.put(n, new DagNodeCheck(n));
        }
        List<String> way = new ArrayList<>();
        DagNodeCheck startCheck = nodeCheckMap.get(newNode);
        if (startCheck != null && hasCycle(startCheck, nodeCheckMap, way)) {
            return way;
        }
        return Collections.emptyList();
    }

    private static boolean hasCycle(DagNodeCheck current, Map<? extends DagNode<?, ?>, DagNodeCheck> nodeCheckMap, List<String> way) {
        current.setBeingVisited(true);
        way.add(current.node.getName());

        for (DagNode<?, ?> node : current.node.getDepends()) {
            DagNodeCheck neighbor = nodeCheckMap.get(node);
            if (neighbor == null) {
                continue;
            }
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
