package com.teumta.server.realtime;

import com.teumta.server.station.StationResponse;
import com.teumta.server.timetable.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 1호선 나무 모양(역마다 연천 쪽 이웃 역 parent)으로 "이 열차가 이 역을 지나는지, 몇 역 남았는지"를 판단한다
final class RouteTree {
    record Node(String id, String name, String parentId, boolean expressStop) {
    }

    private final Map<String, Node> byId = new HashMap<>();
    private final Map<String, Node> byName = new HashMap<>();
    private final Set<String> hasChild = new HashSet<>();

    RouteTree(List<StationResponse> stations) {
        for (StationResponse s : stations) {
            Node node = new Node(s.id(), s.name(), s.parentId(), s.expressStop());
            byId.put(node.id(), node);
            byName.put(node.name(), node);
            if (s.parentId() != null) {
                hasChild.add(s.parentId());
            }
        }
    }

    Node byId(String id) {
        return byId.get(id);
    }

    Node byName(String name) {
        return name == null ? null : byName.get(name);
    }

    // node에서 연천 쪽으로 몇 역 올라가야 ancestor인가. ancestor가 node의 연천 쪽에 없으면 -1 (같은 역은 0)
    int stepsUp(Node node, Node ancestor) {
        int steps = 0;
        String id = node.id();
        while (id != null) {
            if (id.equals(ancestor.id())) {
                return steps;
            }
            id = byId.get(id).parentId();
            steps++;
        }
        return -1;
    }

    // from에서 to로 갈 때의 방향. 갈림길 너머(인천 -> 가산디지털단지처럼 갈아타야 하는 곳)는 null
    Direction direction(Node from, Node to) {
        if (stepsUp(from, to) > 0) {
            return Direction.UP;
        }
        if (stepsUp(to, from) > 0) {
            return Direction.DOWN;
        }
        return null;
    }

    // direction으로 a에서 b까지 몇 역인가(같은 역 0). 그 방향으로 갈 수 없으면 -1
    int stepsAlong(Direction direction, Node a, Node b) {
        return direction == Direction.UP ? stepsUp(a, b) : stepsUp(b, a);
    }

    // 그 방향으로 더 갈 역이 없는 끝 역. 하행 끝은 인천·신창·광명·서동탄, 상행 끝은 연천
    boolean isEnd(Node node, Direction direction) {
        return direction == Direction.UP ? node.parentId() == null : !hasChild.contains(node.id());
    }
}
