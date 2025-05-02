package Computer.Engineering.Google.Text.Editor.sync;

import Computer.Engineering.Google.Text.Editor.model.CrdtNode;
import java.util.List;

public interface BroadcastListener {
  void receiveBroadcast(List<CrdtNode> nodes, List<CrdtNode> deleted);

  void receiveCursor(String userId, int cursorPos, String color);
}
