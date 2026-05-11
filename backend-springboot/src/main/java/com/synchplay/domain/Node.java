package com.synchplay.domain;

import java.util.*;

/**
 * 图的节点类
 * 代表一个用户或视频节点
 */
public class Node {
    private String nodeId;          // 唯一标识，格式：user_{id} 或 video_{id}
    private String nodeType;        // 节点类型：user 或 video
    private String originalId;      // 原始ID
    private String displayName;     // 显示名称
    private Map<String, String> attributes;  // 其他属性（如频道名、观看量等）
    private List<Node> neighbors;   // 邻接表：存储邻居节点

    public Node(String nodeId, String nodeType, String originalId, String displayName) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.originalId = originalId;
        this.displayName = displayName;
        this.attributes = new HashMap<>();
        this.neighbors = new ArrayList<>();
    }

    // ============== Getters and Setters ==============
    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getOriginalId() {
        return originalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public List<Node> getNeighbors() {
        return neighbors;
    }

    /**
     * 添加邻居节点
     */
    public void addNeighbor(Node neighbor) {
        if (!neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
        }
    }

    /**
     * 获取节点的度（邻居数量）
     */
    public int getDegree() {
        return neighbors.size();
    }

    /**
     * 判断是否为用户节点
     */
    public boolean isUser() {
        return "user".equals(nodeType);
    }

    /**
     * 判断是否为视频节点
     */
    public boolean isVideo() {
        return "video".equals(nodeType);
    }

    @Override
    public String toString() {
        return String.format("Node[id=%s, type=%s, name=%s, neighbors=%d]", 
                           nodeId, nodeType, displayName, neighbors.size());
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node)) return false;
        return this.nodeId.equals(((Node) obj).nodeId);
    }
}
