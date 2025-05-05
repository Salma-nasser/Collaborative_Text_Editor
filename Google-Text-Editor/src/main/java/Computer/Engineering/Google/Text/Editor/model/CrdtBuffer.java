package Computer.Engineering.Google.Text.Editor.model;

import java.util.*;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

public class CrdtBuffer {
    private List<CrdtNode> nodes;
    private String siteId;
    private int clock;
    private Set<String> deletedSet;
    private List<CrdtNode> deletedNodes = new ArrayList<>();
    private final List<Comment> comments = new ArrayList<>();
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
        // Build the tree structure
        Map<String, List<CrdtNode>> childrenMap = new HashMap<>();
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }

        // Sort children using the natural ordering defined in CrdtNode.compareTo
        for (List<CrdtNode> children : childrenMap.values()) {
            Collections.sort(children); // This will use the compareTo method
        }

        // Debug visualization (keep this for debugging)
        System.out.println("\n===== DOCUMENT TREE STRUCTURE =====");
        System.out.println("Root node: 0");
        buildDocumentWithVisualization("0", childrenMap, new StringBuilder(), "", 0);
        System.out.println("==================================\n");

        // Build document with direct traversal
        StringBuilder doc = new StringBuilder();
        buildDocumentDirect("0", childrenMap, doc);
        return doc.toString();
    }

    // Direct document building with proper traversal
    private void buildDocumentDirect(String nodeId, Map<String, List<CrdtNode>> childrenMap, StringBuilder sb) {
        List<CrdtNode> children = childrenMap.get(nodeId);
        if (children == null)
            return;

        for (CrdtNode child : children) {
            sb.append(child.getCharValue());
            buildDocumentDirect(child.getUniqueId(), childrenMap, sb);
        }
    }

    // Enhanced buildDocument method with tree visualization
    private void buildDocumentWithVisualization(String nodeId, Map<String, List<CrdtNode>> childrenMap,
            StringBuilder document, String prefix, int depth) {
        List<CrdtNode> children = childrenMap.get(nodeId);
        if (children == null)
            return;

        for (int i = 0; i < children.size(); i++) {
            CrdtNode node = children.get(i);
            boolean isLastChild = (i == children.size() - 1);

            // Add the character to the document
            document.append(node.getCharValue());

            // Print tree visualization
            String childPrefix = prefix + (isLastChild ? "   " : "│  ");
            String nodeConnector = isLastChild ? "└─ " : "├─ ";
            System.out.println(prefix + nodeConnector + "'" + node.getCharValue() + "' (ID: " +
                    node.getUniqueId() + ", Parent: " + node.getParentId() +
                    ", Counter: " + node.getCounter() + ", Depth: " + depth + ")");

            // Process children of this node
            buildDocumentWithVisualization(node.getUniqueId(), childrenMap, document, childPrefix, depth + 1);
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

        // Use the same natural ordering as in getDocument
        Collections.sort(children); // This will use the compareTo method

        for (CrdtNode node : children) {
            result.add(node.getUniqueId());
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
        String nodeId = siteId + "-" + clock;

        // Find comments attached to this node
        List<Comment> commentsToRemove = comments.stream()
                .filter(c -> c.isAttachedToNode(nodeId))
                .collect(Collectors.toList());

        // Remove these comments
        comments.removeAll(commentsToRemove);

        // Continue with normal deletion
        CrdtNode nodeToDelete = null;
        for (CrdtNode node : nodes) {
            if (node.getSiteId().equals(siteId) && node.getClock() == clock) {
                nodeToDelete = node;
                break;
            }
        }

        if (nodeToDelete != null) {
            // Get the parent ID before setting deleted flag
            String parentId = nodeToDelete.getParentId();

            // Mark this node as deleted
            nodeToDelete.setDeleted(true);
            deletedNodes.add(nodeToDelete);

            // Important: Find all children of this node and reassign their parent
            for (CrdtNode node : nodes) {
                if (node.getParentId().equals(nodeToDelete.getUniqueId())) {
                    // Reassign the parent to be the deleted node's parent
                    node.setParentId(parentId);
                    logger.debug("Reassigned node " + node.getCharValue() +
                            " from parent " + nodeToDelete.getUniqueId() + " to " + parentId);
                }
            }

            logger.debug("Deleted node: " + nodeToDelete.getUniqueId());
        }

        // Update remaining comment positions
        updateCommentPositions();
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

    // Add a comment to a text selection
    public Comment addComment(String authorId, String authorColor, String content, int startPos, int endPos) {
        if (startPos >= endPos || startPos < 0 || endPos > getDocument().length()) {
            throw new IllegalArgumentException("Invalid comment range");
        }

        String startNodeId = getNodeIdAtPosition(startPos);
        String endNodeId = getNodeIdAtPosition(endPos - 1);

        Comment comment = new Comment(authorId, authorColor, content, startNodeId, endNodeId, startPos, endPos);
        comments.add(comment);
        return comment;
    }

    // Get all comments
    public List<Comment> getComments() {
        return new ArrayList<>(comments);
    }

    // Removed unused buildNodeIdList() method to resolve compilation errors.

    // Helper method to get a list of all node IDs in the document
    public List<String> getNodeIdList() {
        Map<String, List<CrdtNode>> childrenMap = new HashMap<>();
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }

        List<String> nodeIds = new ArrayList<>();
        buildNodeIdList("0", childrenMap, nodeIds);
        return nodeIds;
    }

    // Update comment positions after document changes
    public void updateCommentPositions() {
        // String document = getDocument();
        Map<String, Integer> nodePositions = new HashMap<>();

        // Map node IDs to current positions
        List<String> nodeIds = getNodeIdList();
        for (int i = 0; i < nodeIds.size(); i++) {
            nodePositions.put(nodeIds.get(i), i);
        }

        // Update each comment's positions
        for (Comment comment : comments) {
            Integer startPos = nodePositions.get(comment.getStartNodeId());
            Integer endPos = nodePositions.get(comment.getEndNodeId());

            // If both nodes still exist, update positions
            if (startPos != null && endPos != null) {
                comment.updatePositions(startPos, endPos + 1);
            }
        }
    }

    public void addExistingComment(Comment comment) {
        // Check if comment already exists
        if (comments.stream().noneMatch(c -> c.getCommentId().equals(comment.getCommentId()))) {
            comments.add(comment);
        }
    }

    /**
     * Removes a comment with the specified ID from the buffer
     * 
     * @param commentId The ID of the comment to remove
     * @return true if the comment was found and removed, false otherwise
     */
    public boolean removeComment(String commentId) {
        // Use Iterator to safely remove while iterating
        for (Iterator<Comment> iterator = comments.iterator(); iterator.hasNext();) {
            Comment comment = iterator.next();
            if (comment.getCommentId().equals(commentId)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
