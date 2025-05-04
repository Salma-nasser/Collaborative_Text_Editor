package Computer.Engineering.Google.Text.Editor.sync;

import java.util.List;

import Computer.Engineering.Google.Text.Editor.model.CrdtNode;

public interface BroadcastListener {
    // Document synchronization
    void receiveBroadcast(List<CrdtNode> nodes, List<CrdtNode> deletedNodes);
    
    // Cursor tracking
    void receiveCursor(String userId, int cursorPos, String color);
    
    // User presence
    void receiveUserPresence(String userId, String role, boolean isOnline, String sessionCode);
    
    // Document state transfer
    void receiveDocumentRequest(String requesterId, String sessionCode);
    void receiveDocumentState(String documentContent);
    
    // Required getters
    String getSessionCode();
    String getUserId();
}