package Computer.Engineering.Google.Text.Editor.UserInterface;
import Computer.Engineering.Google.Text.Editor.model.CrdtBuffer;
import Computer.Engineering.Google.Text.Editor.model.CrdtNode;
import Computer.Engineering.Google.Text.Editor.sync.Broadcaster;
//import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
//import com.vaadin.flow.component.html.Div;
//import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import java.util.ArrayList;
import java.util.List;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


@Route("")
public class EditorView extends VerticalLayout implements Broadcaster.BroadcastListener {

    private CrdtBuffer crdtBuffer;
    private TextArea editor;
    private boolean isEditor = true; // Always allow editing for testing CRDT

    public EditorView() {
        crdtBuffer = new CrdtBuffer("site" + System.currentTimeMillis());

        // Top Toolbar Buttons (Optional for future features like undo/redo)
        Button undoButton = new Button("Undo"); // Not yet wired
        Button redoButton = new Button("Redo");
        // Button importButton = new Button("Import");
        // 
        // Import: Upload a .txt file
        MemoryBuffer buffer = new MemoryBuffer();
        Upload importUpload = new Upload(buffer);
        importUpload.setAcceptedFileTypes(".txt");
        importUpload.addSucceededListener(event -> {
            try {
                String content = new String(buffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                editor.setValue(content); // this will trigger CRDT sync through valueChangeListener
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        importUpload.setUploadButton(new Button("Import"));
        importUpload.setDropAllowed(false);

        // Export: Download current text
        //Button exportButton = new Button("Export");
        Anchor exportAnchor = new Anchor();
        Button exportButton = new Button("Export");
        
        // The Anchor element's href will be set dynamically when the export button is clicked
        //Anchor exportAnchor = new Anchor();
        exportAnchor.getElement().setAttribute("download", true); // Ensure it downloads as a .txt file
        //exportAnchor.setVisible(false); // Hide it initially
        add(exportAnchor); // Add it to the layout
        
        // Configure the export button
        exportButton.addClickListener(e -> {
            // Get the document content
            String content = editor.getValue();
        
            // Create a StreamResource from the content
            StreamResource resource = new StreamResource("document.txt", () ->
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
            );
        
            // Update the exportAnchor with the new resource
            exportAnchor.setHref(resource);
            exportAnchor.setVisible(true); // Make it visible temporarily
        
            // Trigger the download
            exportAnchor.getElement().callJsFunction("click");
        
            // Hide the exportAnchor again after the click
           // exportAnchor.setVisible(false);
        });
        
        // Set visibility of the export anchor as needed (it doesn't need to be visible in the layout)
        exportAnchor.setVisible(false);
        
        HorizontalLayout topBanner = new HorizontalLayout(
                new HorizontalLayout(undoButton, redoButton),
                new HorizontalLayout(importUpload, exportButton));
        topBanner.setWidthFull();
        topBanner.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topBanner.setAlignItems(FlexComponent.Alignment.CENTER);
        topBanner.getStyle().set("background-color", "#f0f0f0").set("padding", "10px");

        // Editor Setup: TextArea with CRDT logic for typing
        editor = new TextArea();
        editor.setWidthFull();
        editor.setHeightFull();
        editor.getStyle().set("resize", "none");
        editor.setReadOnly(false); // Allow text editing

        editor.addValueChangeListener(event -> {
            if (!event.isFromClient())
                return;

            String newText = event.getValue();
            String oldText = crdtBuffer.getDocument();

            int oldLen = oldText.length();
            int newLen = newText.length();

            int start = 0;
            while (start < Math.min(oldLen, newLen) && oldText.charAt(start) == newText.charAt(start)) {
                start++;
            }

            int endOld = oldLen - 1;
            int endNew = newLen - 1;
            while (endOld >= start && endNew >= start && oldText.charAt(endOld) == newText.charAt(endNew)) {
                endOld--;
                endNew--;
            }

            // Handle deletions
            for (int i = start; i <= endOld; i++) {
                String nodeIdToDelete = crdtBuffer.getNodeIdAtPosition(start); // always delete at logical position
                if (!nodeIdToDelete.equals("0")) {
                    String[] parts = nodeIdToDelete.split("-");
                    crdtBuffer.delete(parts[0], Integer.parseInt(parts[1]));
                }
            }

            // Handle insertions
            for (int i = start; i <= endNew; i++) {
                char c = newText.charAt(i);
                String parentId = crdtBuffer.getNodeIdAtPosition(i - 1); // insert after previous char
                crdtBuffer.insert(c, parentId == null ? "0" : parentId);
            }

            Broadcaster.broadcast(
                    new ArrayList<>(crdtBuffer.getAllNodes()),
                    new ArrayList<>(crdtBuffer.getDeletedNodes()));
        });

        // Layout for the Editor Area
        HorizontalLayout mainArea = new HorizontalLayout(editor);
        mainArea.setSizeFull();
        mainArea.setFlexGrow(1, editor);

        // Main Layout setup
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);
        add(topBanner, mainArea);
        //add(exportAnchor);
        expand(mainArea);
    }

    // Server push updates (for real-time syncing)
    @Override
    public void receiveBroadcast(List<CrdtNode> incomingNodes, List<CrdtNode> incomingDeleted) {
        System.out.println("Receiving broadcast with nodes: " + incomingNodes.size());

        getUI().ifPresent(ui -> ui.access(() -> {
            crdtBuffer.merge(incomingNodes, incomingDeleted);
            String doc = crdtBuffer.getDocument();
            System.out.println("Updated doc: " + doc);
            editor.setValue(doc);
        }));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        Broadcaster.register(this);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        Broadcaster.unregister(this);
    }
}
