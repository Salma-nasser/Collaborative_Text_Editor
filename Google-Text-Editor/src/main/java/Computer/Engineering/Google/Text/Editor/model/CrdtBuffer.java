package Computer.Engineering.Google.Text.Editor.model;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrdtBuffer {
    private List<CrdtNode> nodes;
    private String siteId;
    private int clock;
    private Set<String> deletedSet;
    // Add to CrdtBuffer class fields
    private final Stack<Operation> undoStack = new Stack<>();
    private final Stack<Operation> redoStack = new Stack<>();
    private boolean isUndoRedoInProgress = false;
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
    public enum OperationType {
        INSERT, DELETE
    }

    private static class Operation {
        OperationType type;
        CrdtNode node;      // For INSERT operations
        String siteId;      // For DELETE operations
        int clock;          // For DELETE operations

        public Operation(OperationType type, CrdtNode node) {
            this.type = type;
            this.node = node;
        }

        public Operation(OperationType type, String siteId, int clock) {
            this.type = type;
            this.siteId = siteId;
            this.clock = clock;
        }
    }
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
        if (!isUndoRedoInProgress && siteId.equals(this.siteId)) {
            undoStack.push(new Operation(OperationType.INSERT, newNode));
            redoStack.clear();
        }
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
        // Track deletion sequence for batch operations
        List<Operation> batchDeletions = new ArrayList<>();
        for (CrdtNode node : nodes) {
            if (node.getSiteId().equals(siteId) && node.getClock() == clock) {
                node.markDeleted();
                deletedSet.add(node.getUniqueId()); // Track deletions properly
                if (!isUndoRedoInProgress && siteId.equals(this.siteId)) {
                    // Add to temporary batch list
                    batchDeletions.add(new Operation(OperationType.DELETE, siteId, clock));
                }
                logger.debug("Deleted node: " + node.getUniqueId());
                break;
            }
        }
        // Add deletions to undo stack in reverse order (so they undo in correct sequence)
        if (!batchDeletions.isEmpty()) {
            //Collections.reverse(batchDeletions);
            for (Operation op : batchDeletions) {
                undoStack.push(op);
            }
            redoStack.clear();
        }
    }
    private int compareNodesForDisplay(CrdtNode a, CrdtNode b) {
        // First by position in nodes list
        int posCompare = Integer.compare(nodes.indexOf(a), nodes.indexOf(b));
        if (posCompare != 0) return posCompare;

        // Then by CRDT properties as tiebreaker
        return a.compareTo(b);
    }
    public void printOperationSequence() {
        System.out.println("Current Operation Sequence:");
        nodes.stream()
                .filter(n -> !n.isDeleted())
                .sorted(this::compareNodesForDisplay)
                .forEach(n -> System.out.println(n.getCharValue() + "(" + n.getUniqueId() + ")"));
    }

    public synchronized boolean undo() {
        if (undoStack.isEmpty()) return false;
        isUndoRedoInProgress = true;
        Operation op = undoStack.pop();

        switch (op.type) {
            case INSERT:
                // Undo insert = delete the node
                delete(op.node.getSiteId(), op.node.getClock());
                // Push to redo stack
                redoStack.push(new Operation(OperationType.INSERT, op.node));
                break;

            case DELETE:
                // Undo delete = restore the node
                for (CrdtNode node : nodes) {
                    if (node.getSiteId().equals(op.siteId) && node.getClock() == op.clock) {
                        node.markNotDeleted();
                        deletedSet.remove(node.getUniqueId());
                        // Push to redo stack
                        redoStack.push(new Operation(OperationType.DELETE, op.siteId, op.clock));
                        break;
                    }
                }
                break;
        }
        isUndoRedoInProgress = false;
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            logger.debug("Redo stack is empty");
            return false;
        }

        isUndoRedoInProgress = true;

        try {
            Operation op = redoStack.pop();
            logger.debug("Executing redo operation: " + op.type);

            switch (op.type) {
                case INSERT:
                    // For redo insert, we need to create a new node with the same properties
                    CrdtNode newNode = new CrdtNode(
                            op.node.getSiteId(),
                            op.node.getClock(),
                            op.node.getCounter(),
                            op.node.getParentId(),
                            op.node.getCharValue()
                    );
                    nodes.add(newNode);
                    Collections.sort(nodes);
                    undoStack.push(new Operation(OperationType.INSERT, newNode));
                    logger.debug("Redo INSERT: Added node " + newNode.getUniqueId());
                    break;

                case DELETE:
                    // For redo delete, we need to find and mark the node as deleted
                    boolean found = false;
                    for (CrdtNode node : nodes) {
                        if (node.getSiteId().equals(op.siteId) && node.getClock() == op.clock) {
                            node.markDeleted();
                            deletedSet.add(node.getUniqueId());
                            undoStack.push(new Operation(OperationType.DELETE, op.siteId, op.clock));
                            logger.debug("Redo DELETE: Marked node " + node.getUniqueId() + " as deleted");
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        logger.warn("Node to redo-delete not found: " + op.siteId + "-" + op.clock);
                    }
                    break;
            }
            return true;
        } finally {
            isUndoRedoInProgress = false;
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
    public void debugDocumentState() {
        System.out.println("Current Document Nodes:");
        nodes.stream()
                .filter(node -> !node.isDeleted())
                .sorted()
                .forEach(node -> System.out.println(node.getCharValue() + " (" + node.getUniqueId() + ")"));

        System.out.println("Actual Document: " + getDocument());
    }
    public void printStacks() {
        System.out.println("Undo Stack:");
        undoStack.forEach(op -> System.out.println(op.type + " " +
                (op.node != null ? op.node.getUniqueId() : op.siteId + "-" + op.clock)));

        System.out.println("Redo Stack:");
        redoStack.forEach(op -> System.out.println(op.type + " " +
                (op.node != null ? op.node.getUniqueId() : op.siteId + "-" + op.clock)));
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
