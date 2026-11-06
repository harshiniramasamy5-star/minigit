package in.nitw.minigit.readmodel;

import in.nitw.minigit.readmodel.GraphOrder.Node;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphOrderTest {

    private static Node n(String hash, long sec, String... parents) {
        return new Node(hash, Instant.ofEpochSecond(sec), List.of(parents));
    }

    private static List<String> hashes(List<Node> nodes) {
        return nodes.stream().map(Node::hash).toList();
    }

    @Test void newestFirstOnALinearHistory() {
        var out = GraphOrder.dateOrder(List.of(n("a", 1), n("c", 3, "b"), n("b", 2, "a")));
        assertEquals(List.of("c", "b", "a"), hashes(out));
    }

    @Test void parentNeverBeforeChildEvenWithEqualTimestamps() {
        // merge m and its parent p share a timestamp; p must still come after m
        var out = GraphOrder.dateOrder(List.of(n("p", 5, "root"), n("m", 5, "p", "side"),
                n("side", 4, "root"), n("root", 1)));
        List<String> order = hashes(out);
        assertEquals(4, order.size());
        assert order.indexOf("m") < order.indexOf("p");
        assert order.indexOf("m") < order.indexOf("side");
        assert order.indexOf("root") == 3;
    }

    @Test void parentsOutsideTheWindowAreIgnored() {
        var out = GraphOrder.dateOrder(List.of(n("b", 2, "a"), n("c", 3, "b")));
        assertEquals(List.of("c", "b"), hashes(out));
    }
}
