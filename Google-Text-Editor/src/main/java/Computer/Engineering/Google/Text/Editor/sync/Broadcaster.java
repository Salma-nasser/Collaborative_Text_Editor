package Computer.Engineering.Google.Text.Editor.sync;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import Computer.Engineering.Google.Text.Editor.model.CrdtNode;

public class Broadcaster {
    private static final List<BroadcastListener> listeners = new CopyOnWriteArrayList<>();

    public interface BroadcastListener {
        void receiveBroadcast(List<CrdtNode> nodes, List<CrdtNode> deletedNodes);
        void receiveCursor(String userId, int cursorPos, String color);
        void receiveUserPresence(String userId, String role, boolean isOnline, String sessionCode);
        void receiveDocumentRequest(String requesterId, String sessionCode);
        void receiveDocumentState(String documentContent);
        String getUserId();
        String getSessionCode();
    }

    public static void broadcast(List<CrdtNode> nodes, List<CrdtNode> deletedNodes, String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        listeners.forEach(listener -> {
            if (baseSessionCode.equals(getBaseSessionCode(listener.getSessionCode()))) {
                listener.receiveBroadcast(nodes, deletedNodes);
            }
        });
    }

    public static void broadcastCursor(String userId, int cursorPos, String color, String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        listeners.forEach(listener -> {
            if (baseSessionCode.equals(getBaseSessionCode(listener.getSessionCode()))) {
                listener.receiveCursor(userId, cursorPos, color);
            }
        });
    }

    public static void broadcastPresence(String userId, String role, boolean isOnline, String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        listeners.forEach(listener -> {
            if (baseSessionCode.equals(getBaseSessionCode(listener.getSessionCode()))) {
                listener.receiveUserPresence(userId, role, isOnline, baseSessionCode);
            }
        });
    }

    public static void requestDocumentState(String userId, String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        listeners.forEach(listener -> {
            if (baseSessionCode.equals(getBaseSessionCode(listener.getSessionCode()))) {
                listener.receiveDocumentRequest(userId, baseSessionCode);
            }
        });
    }

    public static void sendDocumentState(String targetUserId, String documentContent) {
        listeners.stream()
            .filter(listener -> targetUserId.equals(listener.getUserId()))
            .forEach(listener -> listener.receiveDocumentState(documentContent));
    }

    private static String getBaseSessionCode(String code) {
        if (code == null) return "";
        if (code.endsWith("-view") || code.endsWith("-edit")) {
            return code.substring(0, code.lastIndexOf('-'));
        }
        return code;
    }

    public static void register(BroadcastListener listener) {
        listeners.add(listener);
    }

    public static void unregister(BroadcastListener listener) {
        listeners.remove(listener);
    }
}