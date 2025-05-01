package Computer.Engineering.Google.Text.Editor.controller;

import Computer.Engineering.Google.Text.Editor.services.CrdtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class EditorWebSocketController {

  @Autowired
  private CrdtService crdtService;

  @MessageMapping("/edit")
  @SendTo("/topic/updates")
  public String handleEdit(String input) {
    // Example: input = "insert|H|0" or "delete|site1|3"
    String[] parts = input.split("\\|");
    if (parts[0].equalsIgnoreCase("insert")) {
      crdtService.insert(parts[1].charAt(0), parts[2]); // char, parentId
    } else if (parts[0].equalsIgnoreCase("delete")) {
      crdtService.delete(parts[1], Integer.parseInt(parts[2]));
    }

    return crdtService.getDocument(); // Return latest document for broadcasting
  }
}
