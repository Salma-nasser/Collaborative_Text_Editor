package Computer.Engineering.Google.Text.Editor;


import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.VaadinService;

import Computer.Engineering.Google.Text.Editor.services.UserRegistry; // Replace with the actual package of UserRegistry// Replace with the actual package of UserRegistry

@Push
public class AppShell implements AppShellConfigurator {
    // This class is used to configure the app shell for the Vaadin application.
    // It can be used to set up things like push notifications, themes, etc.
    // Currently, it only enables push notifications. 
    
    // In your main UI or Application class
    public void configureSessionDestroyListener() {
        VaadinService.getCurrent().addSessionDestroyListener(event -> {
            // You need a way to map session to userId
            // For example, store userId in session attribute
            String userId = (String) event.getSession().getAttribute("userId");
            if (userId != null) {
                UserRegistry.getInstance().unregisterUser(userId);
            }
        });
    }
}
