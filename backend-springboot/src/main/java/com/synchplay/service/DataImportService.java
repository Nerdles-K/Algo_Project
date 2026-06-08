package com.synchplay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time CSV → Postgres import. Idempotent: skips if nodes/edges already populated.
 * Runs on application startup (CommandLineRunner). Order(1) so it precedes GraphService loading.
 */
@Component
@Order(1)
public class DataImportService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);

    private final JdbcTemplate jdbc;
    private final String nodesCsv;
    private final String edgesCsv;

    public DataImportService(JdbcTemplate jdbc,
                             @Value("${synchplay.data.nodes-csv}") String nodesCsv,
                             @Value("${synchplay.data.edges-csv}") String edgesCsv) {
        this.jdbc = jdbc;
        this.nodesCsv = nodesCsv;
        this.edgesCsv = edgesCsv;
    }

    @Override
    public void run(String... args) {
        long nodeCount = jdbc.queryForObject("SELECT COUNT(*) FROM nodes", Long.class);
        long edgeCount = jdbc.queryForObject("SELECT COUNT(*) FROM edges", Long.class);
        if (nodeCount > 0 && edgeCount > 0) {
            log.info("CSV import skipped: nodes={}, edges={} already present", nodeCount, edgeCount);
            backfillNodeMetadata();
            return;
        }
        log.info("Empty DB detected; importing CSV from {} and {}", nodesCsv, edgesCsv);
        importNodes();
        importEdges();
        long n = jdbc.queryForObject("SELECT COUNT(*) FROM nodes", Long.class);
        long e = jdbc.queryForObject("SELECT COUNT(*) FROM edges", Long.class);
        log.info("CSV import complete: {} nodes, {} edges", n, e);
    }

    /**
     * Idempotent: backfills category/published_at for existing video nodes that lack them,
     * reading from the bundled nodes CSV. Lets an already-populated DB (e.g. an existing Railway
     * deployment) pick up the V6 columns on the next boot without a wipe/re-import.
     */
    private void backfillNodeMetadata() {
        Long missing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM nodes WHERE node_type='video' AND category IS NULL", Long.class);
        if (missing == null || missing == 0L) {
            return; // nothing to do
        }
        List<Object[]> batch = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(nodesCsv))) {
            String header = br.readLine();   // skip
            if (header == null) return;
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = splitCsv(line, 10);
                if (!"video".equals(f[1])) continue;
                String category    = f[8].isEmpty() ? null : f[8];
                String publishedAt = f[9].isEmpty() ? null : f[9];
                if (category == null && publishedAt == null) continue;
                batch.add(new Object[]{category, publishedAt, f[0]});
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read nodes CSV for backfill: " + nodesCsv, ex);
        }
        int[] updated = jdbc.batchUpdate(
            "UPDATE nodes SET category = ?, published_at = ?::timestamptz " +
            "WHERE node_id = ? AND category IS NULL",
            batch);
        int total = 0;
        for (int u : updated) total += u;
        log.info("Backfilled category/published_at for {} existing video nodes from CSV", total);
    }

    private void importNodes() {
        List<Object[]> batch = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(nodesCsv))) {
            String header = br.readLine();   // skip
            if (header == null) throw new IllegalStateException("Empty nodes CSV: " + nodesCsv);
            String line;
            while ((line = br.readLine()) != null) {
                // tags=8th col (V5); category/published_at=9th/10th col (V6). Older CSVs lacking
                // them still import (splitCsv pads missing trailing columns with "").
                String[] f = splitCsv(line, 10);
                String nodeId      = f[0];
                String nodeType    = f[1];
                String originalId  = f[2];
                String displayName = f[3];
                String channel     = f[4];
                Long   views       = parseLongOrNull(f[5]);
                Long   likes       = parseLongOrNull(f[6]);
                String tags        = f[7].isEmpty() ? null : f[7];
                String category    = f[8].isEmpty() ? null : f[8];
                String publishedAt = f[9].isEmpty() ? null : f[9];
                batch.add(new Object[]{nodeId, nodeType, originalId, displayName, channel, views, likes, tags, category, publishedAt});
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read nodes CSV: " + nodesCsv, ex);
        }
        // INSERT ... ON CONFLICT DO NOTHING — collapses the 500→483 dedup the v1 in-memory loader does
        jdbc.batchUpdate(
            "INSERT INTO nodes(node_id, node_type, original_id, display_name, channel, views, likes, tags, category, published_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?, ?::timestamptz) ON CONFLICT (node_id) DO NOTHING",
            batch);
        log.info("Imported {} node rows from CSV (after dedup, in-DB count = SELECT COUNT(*))", batch.size());
    }

    private void importEdges() {
        List<Object[]> batch = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(edgesCsv))) {
            String header = br.readLine();
            if (header == null) throw new IllegalStateException("Empty edges CSV: " + edgesCsv);
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = splitCsv(line, 4);
                String src       = f[0];
                String dst       = f[1];
                String edgeType  = f[2];
                double weight    = f[3].isEmpty() ? 1.0 : Double.parseDouble(f[3]);
                batch.add(new Object[]{src, dst, edgeType, weight});
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read edges CSV: " + edgesCsv, ex);
        }
        // Skip edges where src or dst dropped during node dedup (FK would fail).
        // Use a not-exists guard inline.
        jdbc.batchUpdate(
            "INSERT INTO edges(src, dst, edge_type, weight) " +
            "SELECT ?, ?, ?, ? " +
            "WHERE EXISTS (SELECT 1 FROM nodes WHERE node_id = ?) " +
            "  AND EXISTS (SELECT 1 FROM nodes WHERE node_id = ?)",
            batch.stream().map(r -> new Object[]{r[0], r[1], r[2], r[3], r[0], r[1]}).toList());
        log.info("Imported edge rows (raw CSV rows = {})", batch.size());
    }

    /** Minimal CSV split: handles simple cases (no embedded commas in quoted fields except channel). */
    private static String[] splitCsv(String line, int expected) {
        // Properly handle quoted fields containing commas
        List<String> out = new ArrayList<>(expected);
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        while (out.size() < expected) out.add("");
        return out.toArray(new String[0]);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException ex) { return null; }
    }
}
