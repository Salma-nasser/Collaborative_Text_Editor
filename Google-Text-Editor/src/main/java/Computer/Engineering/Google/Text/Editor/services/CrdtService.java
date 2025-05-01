package Computer.Engineering.Google.Text.Editor.services;

import Computer.Engineering.Google.Text.Editor.model.CrdtBuffer;
import org.springframework.stereotype.Service;

@Service
public class CrdtService {

    private final CrdtBuffer buffer = new CrdtBuffer("server"); // Server as siteId

    public synchronized void insert(char ch, String parentId) {
        buffer.insert(ch, parentId);
    }

    public synchronized void delete(String siteId, int clock) {
        buffer.delete(siteId, clock);
    }

    public synchronized String getDocument() {
        return buffer.getDocument();
    }

    public synchronized void print() {
        buffer.printBuffer();
    }
}
