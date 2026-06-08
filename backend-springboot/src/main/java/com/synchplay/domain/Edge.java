package com.synchplay.domain;

/**
 * 图的边类。权重为静态权重（social=1.0 / similar=0.5 / watch=0.1）。
 */
public class Edge {
    private Node source;        // 源节点
    private Node target;        // 目标节点
    private String edgeType;    // 边类型：social（社交）、watch（观看）、similar（相似）
    private double weight;      // 边的权重

    public Edge(Node source, Node target, String edgeType, double weight) {
        this.source = source;
        this.target = target;
        this.edgeType = edgeType;
        this.weight = weight;
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

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    // 可选：方便调试的toString方法（不影响运行）
    @Override
    public String toString() {
        return String.format("Edge[%s -> %s, type=%s, weight=%.2f]",
                source.getNodeId(), target.getNodeId(), edgeType, weight);
    }
}
