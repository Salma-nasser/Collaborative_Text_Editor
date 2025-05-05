package Computer.Engineering.Google.Text.Editor.UserInterface;

import com.vaadin.flow.component.html.Div;

/**
 * Simple tracking class for remote user cursors
 */
public class CursorOverlay extends Div {
    private final String userId;
    private final String color;
    private final String role;
    
    public CursorOverlay(String userId, String color, String role) {
        this.userId = userId;
        this.color = color;
        this.role = role;
    }
    
    public void updatePosition(int x, int y, int height) {
        // This is now handled directly in JavaScript
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getColor() {
        return color;
    }
    
    public String getRole() {
        return role;
    }
}