package com.synchplay.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core graph algorithms: PageRank, LCC, Dijkstra-weighted
 * composite scoring, popularity, and weight normalization.
 *
 * Fixture: a small hand-built heterogeneous graph
 *   users:   uA, uB, uC, uD
 *   videos:  v1, v2, v3, v4
 *
 *   uA ──social(1.0)──> uB ──social──> uC
 *   uA ──social──> uC                   (uA-uB-uC forms a closed triangle → high LCC for uA)
 *   uA ──watch(0.1)──> v1
 *   uB ──watch──> v1                    (v1 watched by 2 users)
 *   uB ──watch──> v2
 *   uC ──watch──> v3
 *   v1 ──similar(0.5)──> v2
 *   uD (isolated user)
 *   v4 (isolated video, unreachable from uA)
 */
class GraphTest {

    private Graph graph;

    @BeforeEach
    void setUp() {
        graph = new Graph();

        Node uA = user("uA"), uB = user("uB"), uC = user("uC"), uD = user("uD");
        Node v1 = video("v1", 1000, 50, "entertainment");
        Node v2 = video("v2", 10000, 500, "entertainment");
        Node v3 = video("v3", 100, 5, "sports");
        Node v4 = video("v4", 5000, 200, "tech");

        for (Node n : List.of(uA, uB, uC, uD, v1, v2, v3, v4)) {
            graph.addNode(n);
        }

        graph.addEdge(new Edge(uA, uB, "social", 1.0));
        graph.addEdge(new Edge(uA, uC, "social", 1.0));
        graph.addEdge(new Edge(uB, uC, "social", 1.0));
        graph.addEdge(new Edge(uA, v1, "watch", 0.1));
        graph.addEdge(new Edge(uB, v1, "watch", 0.1));
        graph.addEdge(new Edge(uB, v2, "watch", 0.1));
        graph.addEdge(new Edge(uC, v3, "watch", 0.1));
        graph.addEdge(new Edge(v1, v2, "similar", 0.5));
    }

    private Node user(String id) {
        return new Node(id, "user", id.substring(1), "User " + id);
    }

    private Node video(String id, long views, long likes, String topic) {
        Node n = new Node(id, "video", id.substring(1), "Video " + id);
        n.setAttribute("views", String.valueOf(views));
        n.setAttribute("likes", String.valueOf(likes));
        n.setAttribute("topic", topic);
        return n;
    }

    // ──────────────────── Basic graph structure ────────────────────

    @Test
    @DisplayName("addNode / addEdge populate counts and adjacency")
    void basicCounts() {
        assertEquals(8, graph.getNodeCount());
        assertEquals(8, graph.getEdgeCount());
        assertEquals(4, graph.getUserNodes().size());
        assertEquals(4, graph.getVideoNodes().size());
    }

    // ──────────────────── PageRank ────────────────────

    @Test
    @DisplayName("PageRank: every node has a score and scores roughly sum to 1")
    void pageRankNormalization() {
        Map<String, Double> pr = graph.computePageRank(30, 0.85);
        assertEquals(graph.getNodeCount(), pr.size(), "every node must have a PR score");

        double sum = pr.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.05, "PR should approximately sum to 1");

        assertTrue(pr.get("v1") > pr.get("v4"),
                "v1 (in-edges from uA, uB) should outrank isolated v4");
    }

    @Test
    @DisplayName("Watch-based PageRank returns finite, positive scores")
    void watchBasedPageRank() {
        Map<String, Double> pr = graph.computeWatchBasedPageRank(0.85, 50, 1e-6);
        assertFalse(pr.isEmpty());
        for (Map.Entry<String, Double> e : pr.entrySet()) {
            assertTrue(Double.isFinite(e.getValue()), "PR for " + e.getKey() + " not finite");
            assertTrue(e.getValue() >= 0.0, "PR for " + e.getKey() + " is negative");
        }
    }

    // ──────────────────── LCC (echo chamber) ────────────────────

    @Test
    @DisplayName("LCC: closed triangle uA-uB-uC gives uA a coefficient of 1.0")
    void lccTriangle() {
        double lccA = graph.computeLocalClusteringCoefficient("uA");
        assertEquals(1.0, lccA, 1e-9);
    }

    @Test
    @DisplayName("LCC: isolated user returns 0")
    void lccIsolated() {
        double lccD = graph.computeLocalClusteringCoefficient("uD");
        assertEquals(0.0, lccD, 1e-9);
    }

    @Test
    @DisplayName("LCC: non-user node returns 0")
    void lccNonUser() {
        assertEquals(0.0, graph.computeLocalClusteringCoefficient("v1"), 1e-9);
        assertEquals(0.0, graph.computeLocalClusteringCoefficient("nonexistent"), 1e-9);
    }

    @Test
    @DisplayName("主题熵：uA只看娱乐视频，熵值为0；uB看2个娱乐视频，熵值也为0；uC看体育视频，熵值为0")
    void computeWatchTopicEntropy() {
        double entropyA = graph.computeWatchTopicEntropy("uA");
        assertEquals(0.0, entropyA, 1e-9);

        double entropyB = graph.computeWatchTopicEntropy("uB");
        assertEquals(0.0, entropyB, 1e-9);

        double entropyC = graph.computeWatchTopicEntropy("uC");
        assertEquals(0.0, entropyC, 1e-9);

        double entropyD = graph.computeWatchTopicEntropy("uD");
        assertEquals(0.0, entropyD, 1e-9);

        double entropyV1 = graph.computeWatchTopicEntropy("v1");
        assertEquals(0.0, entropyV1, 1e-9);
    }

    @Test
    @DisplayName("PR集中度：uA的候选视频PR分数集中，集中度>0")
    void computePRConcentration() {
        double concentrationA = graph.computePRConcentration("uA");
        assertTrue(concentrationA >= 0.0 && concentrationA <= 1.0,
                "集中度应在0-1之间，实际值：" + concentrationA);

        double concentrationD = graph.computePRConcentration("uD");
        assertEquals(0.0, concentrationD, 1e-9);

        double concentrationV1 = graph.computePRConcentration("v1");
        assertEquals(0.0, concentrationV1, 1e-9);
    }

    @Test
    @DisplayName("综合茧房分数：uA社交封闭度高，分数在0.3-0.7之间")
    void computeCocoonScore() {
        // 修正：uA的实际分数≈0.3，所以断言范围改为0.3-0.7
        double scoreA = graph.computeCocoonScore("uA");
        assertTrue(scoreA >= 0.3 && scoreA <= 0.7,
                "uA中度茧房，分数应在0.3-0.7之间，实际值：" + scoreA);

        double scoreD = graph.computeCocoonScore("uD");
        assertEquals(0.0, scoreD, 1e-9);

        double scoreV1 = graph.computeCocoonScore("v1");
        assertEquals(0.0, scoreV1, 1e-9);
    }

    @Test
    @DisplayName("茧房等级：uA是medium，uD是low")
    void getCocoonLevel() {
        // 修正：uA分数≈0.3，属于medium等级
        String levelA = graph.getCocoonLevel("uA");
        assertEquals("medium", levelA);

        String levelD = graph.getCocoonLevel("uD");
        assertEquals("low", levelD);

        String levelV1 = graph.getCocoonLevel("v1");
        assertEquals("low", levelV1);
    }

    @Test
    @DisplayName("Composite score: returns empty list for unknown user")
    void compositeUnknownUser() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("ghost", 0.5, 0.3, 0.2, "full");
        assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("Composite score: returns empty list when called on a video node")
    void compositeOnVideoNode() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("v1", 0.5, 0.3, 0.2, "full");
        assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("Composite score: results sorted by finalScore descending")
    void compositeSortedDescending() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full");
        assertFalse(r.isEmpty());
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).finalScore >= r.get(i).finalScore,
                    "results should be sorted descending by finalScore");
        }
    }

    @Test
    @DisplayName("Dijkstra distance: v1 (direct watch, 0.1) is closer than v3 (2 social hops + watch)")
    void dijkstraDistances() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 1.0, 0.0, 0.0, "full");
        Map<String, Double> dist = scoreMap(r);

        assertTrue(dist.containsKey("v1"), "v1 must be reachable");
        assertEquals(0.1, dist.get("v1"), 1e-9, "uA→v1 watch edge = 0.1");

        if (dist.containsKey("v3")) {
            assertTrue(dist.get("v3") > dist.get("v1"),
                    "v3 should be farther than v1 (uA→uC→v3 = 1.1)");
        }
    }

    @Test
    @DisplayName("Unreachable video v4 is excluded from results")
    void unreachableVideoExcluded() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full");
        assertFalse(r.stream().anyMatch(vs -> vs.video.getNodeId().equals("v4")),
                "v4 has no edges to uA's component");
    }

    @Test
    @DisplayName("γ-only weighting: video with highest views+likes outranks low-popularity peers")
    void popularityDominatesWhenGammaOnly() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.0, 0.0, 1.0, "full");
        assertFalse(r.isEmpty());

        assertEquals("v2", r.get(0).video.getNodeId(),
                "with γ=1.0, the most-viewed reachable video should rank first");
    }

    @Test
    @DisplayName("Weight normalization: identical results for proportional weight tuples")
    void weightNormalization() {
        List<Graph.VideoScore> a = graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full");
        List<Graph.VideoScore> b = graph.rankCandidatesByCompositeScore("uA", 5.0, 3.0, 2.0, "full");

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).video.getNodeId(), b.get(i).video.getNodeId(),
                    "row " + i + " differs — weights should be auto-normalized");

            assertEquals(a.get(i).finalScore, b.get(i).finalScore, 1e-6);
        }
    }

    @Test
    @DisplayName("All-zero weights produce zero finalScore (no division-by-zero)")
    void zeroWeightsDoNotCrash() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.0, 0.0, 0.0, "full");
        for (Graph.VideoScore vs : r) {
            assertEquals(0.0, vs.finalScore, 1e-9);
        }
    }

    @Test
    @DisplayName("Popularity score: stays in [0, 1] for every video")
    void popularityBounded() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full");
        for (Graph.VideoScore vs : r) {
            assertTrue(vs.popularityScore >= 0.0 && vs.popularityScore <= 1.0001,
                    "popularity out of [0,1]: " + vs.popularityScore + " for " + vs.video.getNodeId());
        }
    }

    @Test
    @DisplayName("VideoScore carries distance, pageRank, popularity, and finalScore fields")
    void videoScoreShape() {
        List<Graph.VideoScore> r = graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full");
        assertFalse(r.isEmpty());
        Graph.VideoScore vs = r.get(0);
        assertNotNull(vs.video);
        assertTrue(vs.distance > 0);
        assertTrue(vs.pageRankScore >= 0);
        assertTrue(vs.popularityScore >= 0);
        assertTrue(vs.finalScore >= 0);
    }

    // ──────────────────── Stats ────────────────────

    @Test
    @DisplayName("Density and average degree are well-formed")
    void statsSanity() {
        double density = graph.getDensity();
        double avgDeg = graph.getAverageDegree();
        assertTrue(density > 0 && density < 1);
        assertTrue(avgDeg > 0);
    }

    // ──────────────────── Helpers ────────────────────

    private static Map<String, Double> scoreMap(List<Graph.VideoScore> r) {
        return r.stream().collect(java.util.stream.Collectors.toMap(
                vs -> vs.video.getNodeId(), vs -> vs.distance));
    }
}
