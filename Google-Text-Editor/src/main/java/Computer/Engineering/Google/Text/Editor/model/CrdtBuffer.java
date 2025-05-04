package Computer.Engineering.Google.Text.Editor.model;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrdtBuffer {
    private List<CrdtNode> nodes;
    private String siteId;
    private int clock;
    private Set<String> deletedSet;
    Logger logger = LoggerFactory.getLogger(CrdtBuffer.class);

    public CrdtBuffer(String siteId) {
        this.siteId = siteId;
        this.clock = 0;
        this.nodes = new ArrayList<>();
        this.deletedSet = new HashSet<>();
    }

    // basic case
    /*
     * parent is my previous char
     * clock accor to user end count
     * counter
     */

    public void insert(char charValue, String parentId) {
        clock++;
        int counter = 0;

        // Track existing counters for the parentId
        Set<Integer> existingCounters = new HashSet<>();
        for (CrdtNode node : nodes) {
            if (node.getParentId().equals(parentId)) {
                existingCounters.add(node.getCounter());
            }
        }

        // Ensure uniqueness by finding the smallest available counter
        while (existingCounters.contains(counter)) {
            counter++;
        }

        CrdtNode newNode = new CrdtNode(siteId, clock, counter, parentId, charValue);
        nodes.add(newNode);

        Collections.sort(nodes); // Maintain order

        logger.debug("Inserted node: " + newNode);
    }

    public void merge(List<CrdtNode> incomingNodes, List<CrdtNode> incomingDeleted) {
        Map<String, CrdtNode> nodeMap = new HashMap<>();

        for (CrdtNode node : nodes) {
            nodeMap.put(node.getUniqueId(), node);
        }

        for (CrdtNode incoming : incomingNodes) {
            String id = incoming.getUniqueId();

            if (!nodeMap.containsKey(id)) {
                nodes.add(incoming);
            } else {
                CrdtNode local = nodeMap.get(id);
                if (incoming.isDeleted()) {
                    local.markDeleted(); // Correctly mark deletion
                }
            }
        }
        for (CrdtNode deletedNode : incomingDeleted) {
            if (nodeMap.containsKey(deletedNode.getUniqueId())) {
                nodeMap.get(deletedNode.getUniqueId()).markDeleted();
            }
        }
        System.out.println("Merging incoming nodes: " + incomingNodes.size());
        System.out.println("Before merge: " + nodes.size() + " local nodes");

        Collections.sort(nodes);
    }

    public String getDocument() {
        StringBuilder doc = new StringBuilder();
        Map<String, List<CrdtNode>> childrenMap = new HashMap<>();

        // Group nodes by parentId
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }

        System.out.println("\n===== DOCUMENT TREE STRUCTURE =====");
        System.out.println("Root node: 0");
        // Recursively append children in CRDT order
        buildDocument("0", childrenMap, doc, "", 0);
        System.out.println("==================================\n");

        return doc.toString();
    }

    private void buildDocument(String parentId, Map<String, List<CrdtNode>> childrenMap,
            StringBuilder doc, String indent, int depth) {
        List<CrdtNode> children = childrenMap.get(parentId);
        if (children == null)
            return;

        // Sort using your CRDT's compareTo
        children.sort(CrdtNode::compareTo);

        for (CrdtNode node : children) {
            // Print node info with tree visualization
            String nodeInfo = String.format("%s├─ '%c' (ID: %s, Parent: %s, Counter: %d, Depth: %d)",
                    indent, node.getCharValue(), node.getUniqueId(),
                    parentId, node.getCounter(), depth);
            System.out.println(nodeInfo);

            doc.append(node.getCharValue());

            // Recursively process children
            buildDocument(node.getUniqueId(), childrenMap, doc, indent + "│  ", depth + 1);
        }
    }

    // Helper method to find a node's ID by its position
    public String getNodeIdAtPosition(int position) {
        // Build tree structure like getDocument() does
        Map<String, List<CrdtNode>> childrenMap = new HashMap<>();
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }

        List<String> nodeIds = new ArrayList<>();
        buildNodeIdList("0", childrenMap, nodeIds);

        // Now get node at the requested position
        if (position < 0 || position >= nodeIds.size()) {
            return "0"; // Default to root if out of bounds
        }

        return nodeIds.get(position);
    }

    private void buildNodeIdList(String parentId, Map<String, List<CrdtNode>> childrenMap, List<String> result) {
        List<CrdtNode> children = childrenMap.get(parentId);
        if (children == null)
            return;

        // Sort using the same ordering as document building
        children.sort(CrdtNode::compareTo);

        for (CrdtNode node : children) {
            result.add(node.getUniqueId());
            // Recursively add children's IDs
            buildNodeIdList(node.getUniqueId(), childrenMap, result);
        }
    }

    public List<CrdtNode> getAllNodes() {
        return nodes;
    }

    public List<CrdtNode> getDeletedNodes() {
        return nodes.stream().filter(CrdtNode::isDeleted).toList();
    }

    public void delete(String siteId, int clock) {
        for (CrdtNode node : nodes) {
            if (node.getSiteId().equals(siteId) && node.getClock() == clock) {
                node.markDeleted();
                deletedSet.add(node.getUniqueId()); // Track deletions properly
                logger.debug("Deleted node: " + node.getUniqueId());
                break;
            }
        }
    }

    public void printBuffer() {
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                logger.debug("Node: " + node.getCharValue() + " | SiteId: " + node.getSiteId() + " | Clock: "
                        + node.getClock() + " | Counter: " + node.getCounter());
            }
        }
    }

    public String getSiteId() {
        return siteId;
    }

    /**
     * Clears all content from the buffer
     */
    public void clear() {
        this.nodes.clear();
        this.deletedSet.clear();
        this.clock = 0;
    }

    /**
     * Returns the ID of the last inserted node, or "0" if no nodes exist
     * 
     * @return The ID string of the last inserted node
     */
    public String getLastInsertedId() {
        if (nodes.isEmpty()) {
            return "0"; // Default for empty buffer
        }
        List<CrdtNode> sortedNodes = new ArrayList<>(nodes);
        Collections.sort(sortedNodes);

        CrdtNode lastNode = sortedNodes.get(sortedNodes.size() - 1);
        return lastNode.getSiteId() + "-" + lastNode.getClock();
    }

    public String insertAndReturnId(char charValue, String parentId) {
        clock++;
        int counter = 0;
        Set<Integer> existingCounters = new HashSet<>();
        for (CrdtNode node : nodes) {
            if (node.getParentId().equals(parentId)) {
                existingCounters.add(node.getCounter());
            }
        }
        while (existingCounters.contains(counter)) {
            counter++;
        }
        CrdtNode newNode = new CrdtNode(siteId, clock, counter, parentId, charValue);
        nodes.add(newNode);
        Collections.sort(nodes);
        logger.debug("Inserted node: " + newNode);
        return newNode.getUniqueId();
    }
}

// test cases delete and insert
// undo and redo
