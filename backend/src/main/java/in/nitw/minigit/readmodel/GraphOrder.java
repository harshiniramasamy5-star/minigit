package in.nitw.minigit.readmodel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Orders commits for the graph like {@code git log --date-order}: newest first, but a commit
 * is never listed before any of its children. Sorting by time alone is not enough because two
 * commits created in the same millisecond (e.g. a merge right after its parent) could swap.
 * Pure function, no Spring, so it is easy to unit test.
 */
final class GraphOrder {

    private GraphOrder() {}

    record Node(String hash, Instant createdAt, List<String> parents) {}

    static List<Node> dateOrder(List<Node> nodes) {
        Map<String, Node> byHash = new HashMap<>();
        for (Node n : nodes) byHash.put(n.hash(), n);

        // children still waiting to be emitted, counted only inside this window
        Map<String, Integer> pendingChildren = new HashMap<>();
        for (Node n : nodes) {
            for (String p : n.parents()) {
                if (byHash.containsKey(p)) pendingChildren.merge(p, 1, Integer::sum);
            }
        }

        Comparator<Node> newestFirst = Comparator
                .comparing(Node::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()
                .thenComparing(Node::hash, Comparator.reverseOrder());
        PriorityQueue<Node> ready = new PriorityQueue<>(newestFirst);
        for (Node n : nodes) {
            if (!pendingChildren.containsKey(n.hash())) ready.add(n);
        }

        List<Node> out = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            Node n = ready.poll();
            out.add(n);
            for (String p : n.parents()) {
                Node parent = byHash.get(p);
                if (parent == null) continue;
                if (pendingChildren.merge(p, -1, Integer::sum) == 0) {
                    pendingChildren.remove(p);
                    ready.add(parent);
                }
            }
        }
        return out;
    }
}
