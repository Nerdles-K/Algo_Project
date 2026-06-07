package com.synchplay.domain;

/**
 * 图的边类
 */
public class Edge {
    private Node source;        // 源节点
    private Node target;        // 目标节点
    private String edgeType;    // 边类型：social（社交）、watch（观看）、similar（相似）
    private double weight;      // 边的权重
    private long time;          //时间

    public Edge(Node source, Node target, String edgeType, double weight) {
        this.source = source;
        this.target = target;
        this.edgeType = edgeType;
        this.weight = weight;
        this.time = System.currentTimeMillis();
    }

    public double getDynamicWeight() {
        long now = System.currentTimeMillis();
        long deltaTime = now - this.time;
        double lambda = 0.0000001; // 衰减系数
        return this.weight * Math.exp(-lambda * deltaTime);
    }
    
    public double getWeight(){
        return weight;
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

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
    
    @Override
    public String toString() {
        return String.format("Edge[%s -> %s, type=%s, weight=%.2f]",
                source.getNodeId(), target.getNodeId(), edgeType, weight);
    }
}
