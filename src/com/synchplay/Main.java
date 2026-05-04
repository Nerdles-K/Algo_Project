package com.synchplay;

import com.synchplay.model.*;
import com.synchplay.loader.*;
import java.io.*;
import java.util.*;

/**
 * 主测试类
 * 用于验证数据加载和图构建的功能
 */
public class Main {

    public static void main(String[] args) {
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("SynchPlay - 社交视频推荐引擎");
            System.out.println("=".repeat(60) + "\n");

            // 验证数据文件
            DataLoader.validateDataFiles();

            // 加载图数据
            Graph graph = DataLoader.loadGraph();

            // 打印一些基本信息
            System.out.println("\n====== 图的详细信息 ======\n");

            // 用户统计
            List<Node> users = graph.getUserNodes();
            System.out.println("【用户统计】");
            System.out.println("  用户数: " + users.size());
            if (!users.isEmpty()) {
                Node firstUser = users.get(0);
                System.out.println("  示例用户: " + firstUser);
                graph.printNodeNeighbors(firstUser.getNodeId());
            }

            // 视频统计
            List<Node> videos = graph.getVideoNodes();
            System.out.println("\n【视频统计】");
            System.out.println("  视频数: " + videos.size());
            if (!videos.isEmpty()) {
                Node firstVideo = videos.get(0);
                System.out.println("  示例视频: " + firstVideo);
                System.out.println("    标题: " + firstVideo.getDisplayName());
                String channel = firstVideo.getAttribute("channel");
                if (channel != null && !channel.isEmpty()) {
                    System.out.println("    频道: " + channel);
                }
                String views = firstVideo.getAttribute("views");
                if (views != null && !views.isEmpty()) {
                    System.out.println("    观看次数: " + views);
                }
            }

            // 边类型统计
            System.out.println("\n【边类型详情】");
            String[] edgeTypes = {"social", "watch", "similar"};
            for (String type : edgeTypes) {
                List<Edge> edges = graph.getEdgesByType(type);
                System.out.println("  " + type + ": " + edges.size() + " 条边");
                if (!edges.isEmpty()) {
                    Edge sampleEdge = edges.get(0);
                    System.out.println("    示例: " + sampleEdge.getSource().getNodeId() + 
                                     " -> " + sampleEdge.getTarget().getNodeId() + 
                                     " (权重: " + sampleEdge.getWeight() + ")");
                }
            }

            // BFS 候选召回演示
            System.out.println("\n【BFS 候选召回（2-hop）】");
            if (!users.isEmpty()) {
                Node targetUser = users.get(0);
                List<Node> candidateVideos = graph.findCandidateVideosByBFS(targetUser.getNodeId(), 2);
                System.out.println("  目标用户: " + targetUser.getNodeId());
                System.out.println("  候选视频数: " + candidateVideos.size());
                for (int i = 0; i < Math.min(5, candidateVideos.size()); i++) {
                    Node video = candidateVideos.get(i);
                    System.out.println("    " + (i + 1) + ". " + video.getNodeId() + " - " + video.getDisplayName());
                }
            }

            // PageRank 演示
            System.out.println("\n【视频 PageRank Top 5】");
            LinkedHashMap<Node, Double> videoRanks = graph.getVideoPageRankScores(20, 0.85);
            int rank = 1;
            for (Map.Entry<Node, Double> entry : videoRanks.entrySet()) {
                if (rank > 5) break;
                System.out.printf("  %d. %s (score=%.6f)%n",
                        rank,
                        entry.getKey().getNodeId(),
                        entry.getValue());
                rank++;
            }

            // LCC 茧房检测演示
            System.out.println("\n【LCC 茧房检测（局部聚类系数）】");
            LinkedHashMap<Node, Double> userLCC = graph.computeAllUserLCC();
            int displayed = 0;
            int echoChamberCount = 0;
            System.out.println("  Top 5 高茧房风险用户:");
            for (Map.Entry<Node, Double> entry : userLCC.entrySet()) {
                if (displayed >= 5) break;
                String riskFlag = entry.getValue() > 0.5 ? " ⚠️ 高茧房风险" : "";
                if (entry.getValue() > 0.5) echoChamberCount++;
                System.out.printf("    %s  LCC=%.4f%s%n",
                        entry.getKey().getNodeId(), entry.getValue(), riskFlag);
                displayed++;
            }
            // 统计整体茧房情况
            int totalWithNeighbors = 0;
            double sumLCC = 0.0;
            for (Map.Entry<Node, Double> entry : userLCC.entrySet()) {
                if (entry.getValue() > 0) {
                    totalWithNeighbors++;
                    sumLCC += entry.getValue();
                }
            }
            double avgLCC = totalWithNeighbors > 0 ? sumLCC / totalWithNeighbors : 0.0;
            System.out.printf("  有社交连接的用户: %d  平均LCC: %.4f  高茧房风险用户: %d%n",
                    totalWithNeighbors, avgLCC, echoChamberCount);

            // Watch-Based PageRank 演示（组员 Python 算法）
            System.out.println("\n【Watch-Based PageRank Top 5（组员算法, 仅user→video边）】");
            LinkedHashMap<Node, Double> watchRanks = graph.getVideoWatchBasedPageRankScores(0.85, 50, 1e-6);
            int wr = 1;
            for (Map.Entry<Node, Double> entry : watchRanks.entrySet()) {
                if (wr > 5) break;
                System.out.printf("  %d. %s (score=%.6f)%n",
                        wr,
                        entry.getKey().getNodeId(),
                        entry.getValue());
                wr++;
            }

            // 综合打分演示（双模式对比）
            System.out.println("\n【综合打分推荐 Top 5 —— 全图PageRank vs Watch-Based PageRank】");
            if (!users.isEmpty()) {
                Node demoUser = users.get(0);
                System.out.println("  目标用户: " + demoUser.getNodeId());
                System.out.println("\n  --- 全图PageRank模式 (prMode=full) ---");
                List<Graph.VideoScore> fullResults = graph.rankCandidatesByCompositeScore(demoUser.getNodeId(), 0.4, 0.6, "full");
                for (int i = 0; i < Math.min(5, fullResults.size()); i++) {
                    Graph.VideoScore vs = fullResults.get(i);
                    System.out.printf("    %d. %-45s dist=%d final=%.6f%n",
                            i + 1, vs.video.getDisplayName().length() > 43 ? vs.video.getDisplayName().substring(0, 43) : vs.video.getDisplayName(),
                            vs.distance, vs.finalScore);
                }
                System.out.println("\n  --- Watch-Based PageRank模式 (prMode=watch) ---");
                List<Graph.VideoScore> watchResults = graph.rankCandidatesByCompositeScore(demoUser.getNodeId(), 0.4, 0.6, "watch");
                for (int i = 0; i < Math.min(5, watchResults.size()); i++) {
                    Graph.VideoScore vs = watchResults.get(i);
                    System.out.printf("    %d. %-45s dist=%d final=%.6f%n",
                            i + 1, vs.video.getDisplayName().length() > 43 ? vs.video.getDisplayName().substring(0, 43) : vs.video.getDisplayName(),
                            vs.distance, vs.finalScore);
                }
            }

            // 综合打分演示
            if (!users.isEmpty()) {
                Node demoUser = users.get(0);
                List<Graph.VideoScore> scoredResults =
                        graph.rankCandidatesByCompositeScore(demoUser.getNodeId(), 0.4, 0.6);
                System.out.println("  目标用户: " + demoUser.getNodeId());
                System.out.println("  候选视频数: " + scoredResults.size());
                System.out.printf("  %-4s %-45s %-6s %-10s %-8s%n", "排名", "视频标题", "距离", "PageRank", "最终分");
                System.out.println("  " + "-".repeat(86));
                for (int i = 0; i < Math.min(10, scoredResults.size()); i++) {
                    Graph.VideoScore vs = scoredResults.get(i);
                    String title = vs.video.getDisplayName();
                    if (title.length() > 42) title = title.substring(0, 42) + "...";
                    System.out.printf("  %-4d %-45s %-6d %-10.6f %-8.6f%n",
                            i + 1, title, vs.distance, vs.pageRankScore, vs.finalScore);
                }
            }

            // 打印图的邻接表示例
            System.out.println("\n====== 图的邻接表示例 ======\n");
            if (!users.isEmpty()) {
                for (int i = 0; i < Math.min(3, users.size()); i++) {
                    graph.printNodeNeighbors(users.get(i).getNodeId());
                }
            }

            System.out.println("\n✅ 数据加载和图构建成功！");
            System.out.println("=" + "=".repeat(59) + "\n");

        } catch (FileNotFoundException e) {
            System.err.println("❌ 错误: " + e.getMessage());
            System.err.println("\n请先运行数据准备脚本：python3 data_preparation.py");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("❌ IO错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
