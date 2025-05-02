package Computer.Engineering.Google.Text.Editor.services;

import java.util.*;
public class UserRegistry {
    private static final UserRegistry instance = new UserRegistry();
    private final Map<String, String> userColors = new HashMap<>();
    private final List<String> colorPalette = Arrays.asList("#FF5733", "#33C1FF", "#B6FF33", "#FF33B8", "#FFD700", "#8A2BE2");

    private UserRegistry() {}

    public static UserRegistry getInstance() { return instance; }

    public synchronized String registerUser(String userId) {
        if (!userColors.containsKey(userId)) {
            String color = colorPalette.get(userColors.size() % colorPalette.size());
            userColors.put(userId, color);
        }
        return userColors.get(userId);
    }

    public synchronized void unregisterUser(String userId) {
        userColors.remove(userId);
    }

    public Map<String, String> getUserColors() {
        return Collections.unmodifiableMap(userColors);
    }
}