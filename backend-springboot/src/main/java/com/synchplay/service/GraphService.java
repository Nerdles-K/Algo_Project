package com.synchplay.service;

import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * In-memory Graph singleton, hydrated from Postgres at startup.
 * Runs after DataImportService (which guarantees nodes/edges tables are populated).
 *
 * Algorithms (BFS, PageRank, LCC, Composite Scoring) live on the embedded Graph instance.
 * Controllers should obtain the Graph via {@link #getGraph()} and call its methods directly.
 */
@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    private final JdbcTemplate jdbc;
    private final Graph graph = new Graph();
    private FriendRecommendationService friendRec;

    public GraphService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void hydrate() {
        log.info("Hydrating in-memory Graph from Postgres ...");
        long t0 = System.currentTimeMillis();

        jdbc.query("SELECT node_id, node_type, original_id, display_name, channel, views, likes, " +
                   "source, media_path, thumb_path FROM nodes",
            rs -> {
                Node n = new Node(
                    rs.getString("node_id"),
                    rs.getString("node_type"),
                    rs.getString("original_id"),
                    rs.getString("display_name"));
                String channel = rs.getString("channel");
                long views = rs.getLong("views");
                long likes = rs.getLong("likes");
                if (channel != null) n.setAttribute("channel", channel);
                if (!rs.wasNull()) n.setAttribute("likes", String.valueOf(likes));
                if (views > 0)     n.setAttribute("views", String.valueOf(views));
                String source = rs.getString("source");
                if (source != null) n.setAttribute("source", source);
                String mediaPath = rs.getString("media_path");
                if (mediaPath != null) n.setAttribute("mediaPath", mediaPath);
                String thumbPath = rs.getString("thumb_path");
                if (thumbPath != null) n.setAttribute("thumbPath", thumbPath);
                graph.addNode(n);
            });

        jdbc.query("SELECT src, dst, edge_type, weight FROM edges",
            rs -> {
                Node src = graph.getNode(rs.getString("src"));
                Node dst = graph.getNode(rs.getString("dst"));
                if (src != null && dst != null) {
                    graph.addEdge(new Edge(src, dst, rs.getString("edge_type"), rs.getDouble("weight")));
                }
            });

        this.friendRec = new FriendRecommendationService(graph);

        long ms = System.currentTimeMillis() - t0;
        log.info("Graph hydrated in {} ms: {} nodes, {} edges, {} users, {} videos",
            ms, graph.getNodeCount(), graph.getEdgeCount(),
            graph.getUserNodes().size(), graph.getVideoNodes().size());
    }

    public Graph getGraph() {
        return graph;
    }

    public FriendRecommendationService getFriendRecommendationService() {
        return friendRec;
    }
}
