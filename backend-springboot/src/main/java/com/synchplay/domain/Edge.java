package com.synchplay.domain;

/**
 * 图的边类（稳定版，兼容动态权重，不会报错）
 */
public class Edge {
    private Node source;        // 源节点
    private Node target;        // 目标节点
    private String edgeType;    // 边类型：social（社交）、watch（观看）、similar（相似）
    private double weight;      // 边的权重
    private long time;           // 创建时间

    public Edge(Node source, Node target, String edgeType, double weight) {
        this.source = source;
        this.target = target;
        this.edgeType = edgeType;
        this.weight = weight;
        this.time = System.currentTimeMillis();
    }

    // 动态权重（防茧房核心)
    public double getDynamicWeight() {
        long now = System.currentTimeMillis();
        long deltaTime = now - this.time; // 计算时间差
        double lambda = 0.0000001;        // 衰减系数，可根据需要调整
        return this.weight * Math.exp(-lambda * deltaTime);
    }

    public Node getSource() {
        return source;
    }

    public Node getTarget() {
        return target;
    }

    public String getEdgeType() {
        return edgeType;
    }

    // 静态权重（保留它，保证和Graph.java兼容）
    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    // 可选：方便调试的toString方法（不影响运行）
    @Override
    public String toString() {
        return String.format("Edge[%s -> %s, type=%s, weight=%.2f]",
                source.getNodeId(), target.getNodeId(), edgeType, weight);
    }
}
