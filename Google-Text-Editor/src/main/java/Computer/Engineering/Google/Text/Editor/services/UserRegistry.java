package Computer.Engineering.Google.Text.Editor.services;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRegistry {
    private static final UserRegistry instance = new UserRegistry();
    private final Map<String, Map<String, UserInfo>> sessionUsers = new ConcurrentHashMap<>();
    private final List<String> colorPalette = Arrays.asList(
        "#FF5733", "#33C1FF", "#B6FF33", "#FF33B8", "#FFD700", "#8A2BE2"
    );

    public static class UserInfo {
       private String color;
        private String role;
        private String sessionCode;

        public UserInfo(String color, String role, String sessionCode) {
            this.color = color;
            this.role = role;
            this.sessionCode = sessionCode;
        }

        public String getColor() {
            return color;
        }

        public String getRole() {
            return role;
        }

        public String getSessionCode() {
            return sessionCode;
        }
    }


    private UserRegistry() {}

    public static UserRegistry getInstance() {
        return instance;
    }

    public synchronized String registerUser(String userId, String sessionCode, String role) {
    String baseSessionCode = getBaseSessionCode(sessionCode);
    
    // Remove user from any previous session
    sessionUsers.values().forEach(users -> users.remove(userId));
    
    // Add to new session
    sessionUsers.computeIfAbsent(baseSessionCode, k -> new ConcurrentHashMap<>());
    
    // Assign color based on position in session
    int colorIndex = sessionUsers.get(baseSessionCode).size() % colorPalette.size();
    String color = colorPalette.get(colorIndex);
    
    UserInfo info = new UserInfo(color, role, baseSessionCode);
    sessionUsers.get(baseSessionCode).put(userId, info);
    return color;
}

    public synchronized void unregisterUser(String userId) {
        sessionUsers.values().forEach(users -> users.remove(userId));
    }

    public synchronized void unregisterUserFromSession(String userId, String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        if (sessionUsers.containsKey(baseSessionCode)) {
            sessionUsers.get(baseSessionCode).remove(userId);
        }
    }

    public Map<String, UserInfo> getUsersInSession(String sessionCode) {
        String baseSessionCode = getBaseSessionCode(sessionCode);
        return sessionUsers.getOrDefault(baseSessionCode, Collections.emptyMap());
    }

    public String getUserRole(String userId) {
        return sessionUsers.values().stream()
            .filter(users -> users.containsKey(userId))
            .findFirst()
            .map(users -> users.get(userId).role)
            .orElse("viewer");
    }

    public String getUserColor(String userId) {
        return sessionUsers.values().stream()
            .filter(users -> users.containsKey(userId))
            .findFirst()
            .map(users -> users.get(userId).color)
            .orElse("#000000");
    }

    private String getBaseSessionCode(String code) {
        if (code == null) return "";
        if (code.endsWith("-view") || code.endsWith("-edit")) {
            return code.substring(0, code.lastIndexOf('-'));
        }
        return code;
    }
}