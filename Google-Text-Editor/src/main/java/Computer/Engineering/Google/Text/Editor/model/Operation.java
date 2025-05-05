package Computer.Engineering.Google.Text.Editor.model;
import java.util.List;

public class Operation {
    public enum Type { INSERT, DELETE, BULK_DELETE }

    private final Type type;
    private final char value;
    private final String nodeId;
    private final String parentId;
    private final int position;
    private final String previousParentId;
    private final String originalNodeId;
    private final List<Operation> bulkOperations; // For bulk operations

    public Operation(Type type, char value, String nodeId, String parentId,
                     int position, String previousParentId, String originalNodeId) {
        this(type, value, nodeId, parentId, position, previousParentId, originalNodeId, null);
    }

    public Operation(Type type, char value, String nodeId, String parentId,
                     int position, String previousParentId, String originalNodeId,
                     List<Operation> bulkOperations) {
        this.type = type;
        this.value = value;
        this.nodeId = nodeId;
        this.parentId = parentId;
        this.position = position;
        this.previousParentId = previousParentId;
        this.originalNodeId = originalNodeId;
        this.bulkOperations = bulkOperations;
    }
    public List<Operation> getBulkOperations() {
        return bulkOperations;
    }

    public Type getType() { return type; }
    public char getValue() { return value; }
    public String getNodeId() { return nodeId; }
    public String getParentId() { return parentId; }
    public int getPosition() { return position; }
    public String getPreviousParentId() { return previousParentId; }
    public String getOriginalNodeId() { return originalNodeId; }
}