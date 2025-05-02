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

        nodes.sort(Comparator.comparingInt(CrdtNode::getClock)); // Ensures correct order

        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                doc.append(node.getCharValue());
            }
        }

        return doc.toString();
    }

    // Helper method to find a node's ID by its position
    public String getNodeIdAtPosition(int position) {
        int currentPos = 0;
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                if (currentPos == position) {
                    return node.getUniqueId();
                }
                currentPos++;
            }
        }
        return "0"; // Default to beginning
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
}

// test cases delete and insert
// undo and redo
