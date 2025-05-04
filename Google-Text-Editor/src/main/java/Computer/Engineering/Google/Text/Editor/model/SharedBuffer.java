package Computer.Engineering.Google.Text.Editor.model;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class SharedBuffer {
  private static final CrdtBuffer INSTANCE = new CrdtBuffer("shared");
   private static final Map<String, CrdtBuffer> sessionBuffers = new ConcurrentHashMap<>();
    
    
    
    public static CrdtBuffer getInstance(String sessionCode) {
        String baseCode = getBaseSessionCode(sessionCode);
        return sessionBuffers.computeIfAbsent(baseCode, 
            k -> new CrdtBuffer("session-" + baseCode));
    }
    
    public static void clearSession(String sessionCode) {
        sessionBuffers.remove(getBaseSessionCode(sessionCode));
    }
    
    private static String getBaseSessionCode(String code) {
        if (code.endsWith("-view") || code.endsWith("-edit")) {
            return code.substring(0, code.lastIndexOf('-'));
        }
        return code;
    }

    
  private SharedBuffer() {
  }

  public static CrdtBuffer getInstance() {
    return INSTANCE;
  }
}