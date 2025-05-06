package Computer.Engineering.Google.Text.Editor.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class Comment implements Serializable {
  private final String commentId;
  private final String authorId;
  private final String authorColor;
  private final String content;
  private final LocalDateTime timestamp;

  // Range of node IDs this comment is attached to
  private String startNodeId;
  private String endNodeId;

  // Text position data for UI rendering
  private int startPosition;
  private int endPosition;

  public Comment(String authorId, String authorColor, String content,
      String startNodeId, String endNodeId,
      int startPosition, int endPosition) {
    this.commentId = UUID.randomUUID().toString();
    this.authorId = authorId;
    this.authorColor = authorColor;
    this.content = content;
    this.startNodeId = startNodeId;
    this.endNodeId = endNodeId;
    this.startPosition = startPosition;
    this.endPosition = endPosition;
    this.timestamp = LocalDateTime.now();
  }

  // Getters
  public String getCommentId() {
    return commentId;
  }

  public String getAuthorId() {
    return authorId;
  }

  public String getAuthorColor() {
    return authorColor;
  }

  public String getContent() {
    return content;
  }

  public String getStartNodeId() {
    return startNodeId;
  }

  public String getEndNodeId() {
    return endNodeId;
  }

  public int getStartPosition() {
    return startPosition;
  }

  public int getEndPosition() {
    return endPosition;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  // Update positions if document changes
  public void updatePositions(int newStartPos, int newEndPos) {
    this.startPosition = newStartPos;
    this.endPosition = newEndPos;
  }

  // Check if this comment is attached to a specific node
  public boolean isAttachedToNode(String nodeId) {
    return nodeId.equals(startNodeId) || nodeId.equals(endNodeId);
  }
}
