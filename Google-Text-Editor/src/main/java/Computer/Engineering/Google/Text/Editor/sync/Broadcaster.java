package Computer.Engineering.Google.Text.Editor.sync;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import Computer.Engineering.Google.Text.Editor.model.CrdtNode; // Adjust the package path as needed


public class Broadcaster {
    static final List<BroadcastListener> listeners = new CopyOnWriteArrayList<>();

    public interface BroadcastListener {
        void receiveBroadcast(List<CrdtNode> nodes, List<CrdtNode> deletedNodes);
    }

    public static synchronized void broadcast(List<CrdtNode> nodes, List<CrdtNode> deletedNodes) {
        for (BroadcastListener listener : listeners) {
            listener.receiveBroadcast(nodes, deletedNodes);
        }
    }

    public static synchronized void register(BroadcastListener listener) {
        listeners.add(listener);
    }

    public static synchronized void unregister(BroadcastListener listener) {
        listeners.remove(listener);
    }
}

