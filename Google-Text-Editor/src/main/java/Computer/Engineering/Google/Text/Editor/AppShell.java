package Computer.Engineering.Google.Text.Editor;


import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.VaadinService;

import Computer.Engineering.Google.Text.Editor.services.UserRegistry; // Replace with the actual package of UserRegistry// Replace with the actual package of UserRegistry

@Push
public class AppShell implements AppShellConfigurator {
    public void configureSessionDestroyListener() {
        VaadinService.getCurrent().addSessionDestroyListener(event -> {
            String userId = (String) event.getSession().getAttribute("userId");
            if (userId != null) {
                UserRegistry.getInstance().unregisterUser(userId);
            }
        });
    }
}