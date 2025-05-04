package Computer.Engineering.Google.Text.Editor.UserInterface;

import Computer.Engineering.Google.Text.Editor.model.CrdtBuffer;
import Computer.Engineering.Google.Text.Editor.model.CrdtNode;
import Computer.Engineering.Google.Text.Editor.model.SharedBuffer;
import Computer.Engineering.Google.Text.Editor.sync.Broadcaster;
import Computer.Engineering.Google.Text.Editor.services.UserRegistry;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.html.Anchor;

import java.util.ArrayList;
import java.util.List;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import com.vaadin.flow.server.VaadinSession;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.ClientCallable;

@Route("")
public class EditorView extends VerticalLayout implements Broadcaster.BroadcastListener {

    private final CrdtBuffer crdtBuffer = SharedBuffer.getInstance();
    private TextArea editor;
    private int cursorPosition = 0;

    private final VerticalLayout userPanel = new VerticalLayout();
    private final UserRegistry userRegistry = UserRegistry.getInstance();
    private final String userId = UUID.randomUUID().toString();
    private final String userColor = UserRegistry.getInstance().registerUser(userId);
    private final Map<String, Integer> userCursors = new HashMap<>();

    public EditorView() {
        VaadinSession.getCurrent().setAttribute("userId", userId);
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
                // Clear the CRDT buffer before importing new content
                crdtBuffer.clear();
                // Insert each character into the CRDT buffer
                String parentId = "0";
                for (char c : content.toCharArray()) {
                    crdtBuffer.insert(c, parentId);
                    parentId = crdtBuffer.getNodeIdAtPosition(crdtBuffer.getDocument().length() - 1);
                }
                // Update the editor value (this will trigger valueChangeListener, but you can
                // skip broadcasting there)
                editor.setValue(content);
                // Broadcast the new buffer state to all users
                Broadcaster.broadcast(
                        new ArrayList<>(crdtBuffer.getAllNodes()),
                        new ArrayList<>(crdtBuffer.getDeletedNodes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        importUpload.setUploadButton(new Button("Import"));
        importUpload.setDropAllowed(false);

        // Export: Download current text
        // Button exportButton = new Button("Export");
        Anchor exportAnchor = new Anchor();
        Button exportButton = new Button("Export");

        // The Anchor element's href will be set dynamically when the export button is
        // clicked
        // Anchor exportAnchor = new Anchor();
        exportAnchor.getElement().setAttribute("download", true); // Ensure it downloads as a .txt file
        // exportAnchor.setVisible(false); // Hide it initially
        add(exportAnchor); // Add it to the layout

        // Configure the export button
        exportButton.addClickListener(e -> {
            // Get the document content
            String content = editor.getValue();

            StreamResource resource = new StreamResource("document.txt",
                    () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            resource.setContentType("text/plain");

            // Update the exportAnchor with the new resource
            exportAnchor.setHref(resource);
            exportAnchor.setVisible(true); // Make it visible temporarily

            // Trigger the download
            exportAnchor.getElement().callJsFunction("click");

            // Hide the exportAnchor again after the click
            // exportAnchor.setVisible(false);
        });

        // Set visibility of the export anchor as needed (it doesn't need to be visible
        // in the layout)
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

        // Add JS listener to update cursor position
        editor.getElement().addEventListener("input", e -> {
            PendingJavaScriptResult js = editor.getElement().executeJs(
                    "return this.inputElement.selectionStart;");
            js.then(Integer.class, pos -> {
                cursorPosition = pos;
                Broadcaster.broadcastCursor(userId, cursorPosition, userColor);
            });
        });

        // Add comprehensive cursor tracking events
        editor.getElement().executeJs("""
                    this.addEventListener('click', function() {
                        console.log('Cursor click: ' + this.inputElement.selectionStart);
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                    this.addEventListener('keyup', function(e) {
                        if (e.key.startsWith('Arrow') || e.key == 'Home' || e.key == 'End') {
                            console.log('Cursor keyup: ' + this.inputElement.selectionStart);
                            this.$server.handleCursorPosition(this.inputElement.selectionStart);
                        }
                    });
                    this.addEventListener('focus', function() {
                        console.log('Cursor focus: ' + this.inputElement.selectionStart);
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                """);

        editor.addValueChangeListener(event -> {
            if (!event.isFromClient())
                return;

            String newText = event.getValue();
            String oldText = crdtBuffer.getDocument();

            System.out.println("Value change event - cursor at: " + cursorPosition);
            System.out.println("Old text: '" + oldText + "'");
            System.out.println("New text: '" + newText + "'");

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

            System.out.println("Diffing results - start: " + start + ", endOld: " + endOld + ", endNew: " + endNew);

            // Handle deletions
            for (int i = start; i <= endOld; i++) {
                String nodeIdToDelete = crdtBuffer.getNodeIdAtPosition(start); // always delete at logical position
                if (!nodeIdToDelete.equals("0")) {
                    String[] parts = nodeIdToDelete.split("-");
                    crdtBuffer.delete(parts[0], Integer.parseInt(parts[1]));
                }
            }

            // If there are insertions
            if (start <= endNew) {
                // This is the key fix - use cursor position to determine insertion point
                int insertionPoint = start; // The diffing position is your actual insertion point
                System.out.println("Insertion point: " + insertionPoint);

                // Get the node ID exactly at insertionPoint - 1 (character before cursor)
                String parentId = (insertionPoint == 0) ? "0" : crdtBuffer.getNodeIdAtPosition(insertionPoint - 1);
                System.out.println("Using parent ID: " + parentId + " for insertion at position " + insertionPoint);

                // Insert characters one by one, with the first character having the parent ID
                // determined above
                for (int i = start; i <= endNew; i++) {
                    char c = newText.charAt(i);
                    String newNodeId = crdtBuffer.insertAndReturnId(c, parentId);
                    // Important: use the newly inserted node as parent for the next insertion
                    parentId = newNodeId;
                    System.out.println("Inserted '" + c + "' with parent: " + parentId);
                }
            }

            Broadcaster.broadcast(
                    new ArrayList<>(crdtBuffer.getAllNodes()),
                    new ArrayList<>(crdtBuffer.getDeletedNodes()));

            // Debug: print the resulting document and CRDT tree
            System.out.println("Final document: '" + crdtBuffer.getDocument() + "'");
            crdtBuffer.printBuffer();
        });

        // userColor is already initialized during declaration

        userPanel.setWidth("200px");
        userPanel.getStyle().set("background-color", "#f9f9f9");
        updateUserPanel();

        // Add userPanel to your layout
        HorizontalLayout mainArea = new HorizontalLayout(userPanel, editor);
        mainArea.setSizeFull();
        mainArea.setFlexGrow(1, editor);

        // Main Layout setup
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);
        add(topBanner, mainArea);
        // add(exportAnchor);
        expand(mainArea);

    }

    private void updateUserPanel() {
        userPanel.removeAll();
        userPanel.add(new Span("Connected Users:"));
        userRegistry.getUserColors().forEach((id, color) -> {
            Span userLabel = new Span(id.substring(0, 8));
            userLabel.getStyle().set("color", color).set("font-weight", "bold");
            userPanel.add(userLabel);
        });
    }

    private void renderRemoteCursors() {
        // Remove old markers (if any)
        editor.getElement().executeJs(
                "Array.from(this.parentNode.querySelectorAll('.remote-cursor')).forEach(e => e.remove());");
        // Render each remote cursor
        userCursors.forEach((id, pos) -> {
            if (!id.equals(userId)) {
                String color = userRegistry.getUserColors().get(id);
                // JS to insert a colored caret at the correct position
                editor.getElement().executeJs(
                        "const ta = this.inputElement;" +
                                "const rect = ta.getBoundingClientRect();" +
                                "const span = document.createElement('span');" +
                                "span.className = 'remote-cursor';" +
                                "span.style.position = 'absolute';" +
                                "span.style.height = '1.2em';" +
                                "span.style.width = '2px';" +
                                "span.style.background = $1;" +
                                "span.style.left = (ta.selectionStart === $0 ? ta.selectionStart : $0) + 'ch';" +
                                "span.style.top = '0';" +
                                "ta.parentNode.appendChild(span);",
                        pos, color);
            }
        });
    }

    // Server push updates (for real-time syncing)
    @Override
    public void receiveBroadcast(List<CrdtNode> incomingNodes, List<CrdtNode> incomingDeleted) {
        getUI().ifPresent(ui -> ui.access(() -> {
            crdtBuffer.merge(incomingNodes, incomingDeleted);
            String doc = crdtBuffer.getDocument();
            int pos = cursorPosition; // Save current cursor position

            editor.setValue(doc);

            // Restore cursor position (if possible)
            editor.getElement().executeJs(
                    "this.inputElement.setSelectionRange($0, $0);",
                    Math.min(pos, doc.length()));
        }));
    }

    @Override
    public void receiveCursor(String remoteUserId, int pos, String color) {
        if (!remoteUserId.equals(userId)) {
            userCursors.put(remoteUserId, pos);
            userRegistry.registerUser(remoteUserId); // Ensure color is set
            updateUserPanel(); // <-- Add this!
            renderRemoteCursors();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        Broadcaster.register(this);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        Broadcaster.unregister(this);
        userRegistry.unregisterUser(userId);
        userCursors.remove(userId);
        Broadcaster.broadcastCursor(userId, -1, userColor); // -1 means "gone"
        updateUserPanel();
    }

    // Add ClientCallable method to receive cursor position updates
    @ClientCallable
    private void handleCursorPosition(int position) {
        System.out.println("Cursor position updated: " + position +
                " (current document length: " + crdtBuffer.getDocument().length() + ")");

        // Log what character is before cursor
        if (position > 0) {
            String doc = crdtBuffer.getDocument();
            String nodeId = crdtBuffer.getNodeIdAtPosition(position - 1);
            System.out.println("Character before cursor: '" +
                    doc.charAt(position - 1) + "' with ID: " + nodeId);
        }

        cursorPosition = position;
        userCursors.put(userId, position);
        Broadcaster.broadcastCursor(userId, cursorPosition, userColor);
    }
}
