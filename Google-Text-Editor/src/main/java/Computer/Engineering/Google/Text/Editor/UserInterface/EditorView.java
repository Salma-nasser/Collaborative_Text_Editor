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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;

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
    private final String userColor = UserRegistry.getInstance().registerUser(userId, "", "editor"); // Initialize with
    private final Map<String, Integer> userCursors = new HashMap<>();
    private String sessionCode = "";
    private TextField sessionCodeField;
    private Button joinSessionButton;
    private String userRole = "editor";
    private final Map<String, String> userRoles = new HashMap<>();

    private CrdtBuffer getCrdtBuffer() {
        return sessionCode.isEmpty()
                ? new CrdtBuffer("temp")
                : SharedBuffer.getInstance(sessionCode);
    }

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
                        new ArrayList<>(crdtBuffer.getDeletedNodes()), sessionCode);
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
                Broadcaster.broadcastCursor(userId, cursorPosition, userColor, sessionCode);
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

            // Improved deletion logic
            if (endOld >= start) {
                System.out.println("Deleting characters from position " + start + " to " + endOld);
                
                // First, identify all specific nodes that need deletion
                List<String> nodesToDelete = new ArrayList<>();
                for (int pos = start; pos <= endOld; pos++) {
                    String nodeId = crdtBuffer.getNodeIdAtPosition(pos);
                    if (!nodeId.equals("0")) {
                        nodesToDelete.add(nodeId);
                        System.out.println("Marking node for deletion: " + nodeId + " at position " + pos);
                    }
                }
                
                // Then delete them one by one
                for (String nodeId : nodesToDelete) {
                    String[] parts = nodeId.split("-");
                    System.out.println("Deleting node: " + nodeId);
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
                    new ArrayList<>(crdtBuffer.getDeletedNodes()), sessionCode);

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

        initializeSessionComponents();

        HorizontalLayout sessionJoinPanel = new HorizontalLayout(sessionCodeField, joinSessionButton);
        sessionJoinPanel.setWidthFull();
        topBanner.add(sessionJoinPanel); // Add to your existing toolbar

        addEditorStyles();
        setupEnhancedCursorTracking();
        schedulePeriodicCursorUpdates();
    }

    private void updateUserPanel() {
        userPanel.removeAll();
        userPanel.add(new H3("Session: " + (sessionCode.isEmpty() ? "Not joined" : sessionCode)));
        userPanel.add(new Span("Your role: " + userRole));
        userPanel.add(new Hr());

        if (sessionCode.isEmpty()) {
            userPanel.add(new Span("Join a session to see participants"));
            return;
        }

        // Get users in current session only
        Map<String, UserRegistry.UserInfo> users = userRegistry.getUsersInSession(sessionCode);
        String baseSessionCode = getBaseSessionCode(sessionCode);
        userPanel.add(new H3("Session: " + (baseSessionCode.isEmpty() ? "Not joined" : baseSessionCode)));
        userPanel.add(new H4("Connected Users (" + users.size() + ")"));

        users.forEach((id, info) -> {
            HorizontalLayout userEntry = new HorizontalLayout();
            Span userIdSpan = new Span(id.substring(0, 8));
            userIdSpan.getStyle()
                    .set("color", info.getColor()) // Use getter
                    .set("font-weight", "bold");

            Span roleSpan = new Span(info.getRole());
            roleSpan.getStyle()
                    .set("font-style", "italic")
                    .set("margin-left", "5px");

            userEntry.add(userIdSpan, roleSpan);
            userPanel.add(userEntry);
        });
    }

    private void renderRemoteCursors() {
        getUI().ifPresent(ui -> ui.access(() -> {
            // Remove all existing cursors first
            editor.getElement().executeJs(
                    "Array.from(document.querySelectorAll('.remote-cursor')).forEach(e => e.remove());");

            userCursors.forEach((id, pos) -> {
                // Don't render your own cursor and don't render viewers' cursors
                if (!id.equals(userId) && !userRegistry.getUserRole(id).equals("viewer")) {
                    String color = userRegistry.getUserColor(id);
                    String role = userRegistry.getUserRole(id);
                    String shortId = id.substring(0, 6);

                    editor.getElement().executeJs(
                            """
                                        (function() {
                                            const ta = this.inputElement;
                                            if (!ta || pos < 0) return;

                                            // Save original selection
                                            const originalStart = ta.selectionStart;
                                            const originalEnd = ta.selectionEnd;

                                            // Move to position we want to show cursor for
                                            ta.focus();
                                            ta.setSelectionRange($0, $0);

                                            // Get text coordinates (this uses a helper function since position calculation is complex)
                                            function getCaretCoordinates() {
                                                const rect = ta.getBoundingClientRect();
                                                const position = $0;

                                                // Create a mirror div to calculate position
                                                const mirror = document.createElement('div');
                                                mirror.style.position = 'absolute';
                                                mirror.style.top = '0';
                                                mirror.style.left = '0';
                                                mirror.style.visibility = 'hidden';
                                                mirror.style.whiteSpace = 'pre-wrap';
                                                mirror.style.wordWrap = 'break-word';
                                                mirror.style.font = window.getComputedStyle(ta).font;
                                                mirror.style.padding = window.getComputedStyle(ta).padding;

                                                // Add content up to cursor position
                                                const textBeforeCursor = ta.value.substring(0, position);
                                                mirror.textContent = textBeforeCursor;

                                                // Add a span where cursor would be
                                                const span = document.createElement('span');
                                                span.textContent = '.';
                                                mirror.appendChild(span);

                                                document.body.appendChild(mirror);
                                                const spanRect = span.getBoundingClientRect();
                                                document.body.removeChild(mirror);

                                                return {
                                                    left: spanRect.left - rect.left + parseInt(window.getComputedStyle(ta).paddingLeft),
                                                    top: spanRect.top - rect.top
                                                };
                                            }

                                            // Get cursor position
                                            const coords = getCaretCoordinates();
                                            const lineHeight = parseFloat(window.getComputedStyle(ta).lineHeight);

                                            // Create cursor element
                                            const cursor = document.createElement('div');
                                            cursor.className = 'remote-cursor';
                                            cursor.style.position = 'absolute';
                                            cursor.style.backgroundColor = $1;
                                            cursor.style.width = '2px';
                                            cursor.style.height = (lineHeight || 20) + 'px';

                                            // Add user label
                                            const label = document.createElement('div');
                                            label.style.position = 'absolute';
                                            label.style.top = '-20px';
                                            label.style.left = '-3px';
                                            label.style.backgroundColor = $1;
                                            label.style.color = 'white';
                                            label.style.padding = '2px 6px';
                                            label.style.borderRadius = '3px';
                                            label.style.fontSize = '12px';
                                            label.style.whiteSpace = 'nowrap';
                                            label.textContent = $2 + ' (' + $3 + ')';
                                            cursor.appendChild(label);

                                            // Position cursor relative to text area
                                            const rect = ta.getBoundingClientRect();
                                            document.body.appendChild(cursor);

                                            cursor.style.left = (rect.left + coords.left) + 'px';
                                            cursor.style.top = (rect.top + coords.top) + 'px';

                                            // Add animation for blinking effect
                                            cursor.animate([
                                                { opacity: 1 },
                                                { opacity: 0.3 },
                                                { opacity: 1 }
                                            ], {
                                                duration: 1000,
                                                iterations: Infinity
                                            });

                                            // Restore original selection
                                            ta.setSelectionRange(originalStart, originalEnd);
                                        })();
                                    """,
                            pos, color, shortId, role);
                }
            });
        }));
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

            // Add this line to update remote cursors after document changes
            renderRemoteCursors();
        }));
    }

    @Override
    public void receiveCursor(String remoteUserId, int pos, String color) {
        // Don't process your own cursor updates
        if (!remoteUserId.equals(userId)) {
            getUI().ifPresent(ui -> ui.access(() -> {
                // Store cursor position
                userCursors.put(remoteUserId, pos);

                // Re-render all cursors
                renderRemoteCursors();
            }));
        }
    }

    @Override
    public void receiveDocumentRequest(String requesterId, String sessionCode) {
        if (this.sessionCode.equals(sessionCode)) {
            Broadcaster.sendDocumentState(requesterId, getCrdtBuffer().getDocument());
        }
    }

    @Override
    public void receiveDocumentState(String documentContent) {
        getUI().ifPresent(ui -> ui.access(() -> {
            getCrdtBuffer().clear();
            String parentId = "0";
            for (char c : documentContent.toCharArray()) {
                getCrdtBuffer().insert(c, parentId);
                parentId = getCrdtBuffer().getNodeIdAtPosition(getCrdtBuffer().getDocument().length() - 1);
            }
            editor.setValue(documentContent);
        }));
    }

    @Override
    public void receiveUserPresence(String userId, String role, boolean isOnline, String sessionCode) {
        if (!this.sessionCode.equals(sessionCode))
            return;

        getUI().ifPresent(ui -> ui.access(() -> {
            if (isOnline) {
                userRoles.put(userId, role);
                userRegistry.registerUser(userId, sessionCode, role);
            } else {
                userRoles.remove(userId);
                userRegistry.unregisterUserFromSession(userId, sessionCode);
                userCursors.remove(userId);
            }
            updateUserPanel();
            renderRemoteCursors();
        }));
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getSessionCode() {
        return this.sessionCode;
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
        Broadcaster.broadcastCursor(userId, -1, userColor, sessionCode); // -1 means "gone"
        updateUserPanel();
    }

    // Add ClientCallable method to receive cursor position updates
    @ClientCallable
    private void handleCursorPosition(int position) {
        // Only broadcast cursor if we're an editor, not a viewer
        if (!"viewer".equals(userRole)) {
            System.out.println("Cursor position updated: " + position);
            cursorPosition = position;
            userCursors.put(userId, position);
            Broadcaster.broadcastCursor(userId, cursorPosition, userColor, sessionCode);
        }
    }

    private void initializeSessionComponents() {
        sessionCodeField = new TextField("Session Code");
        joinSessionButton = new Button("Join Session", e -> joinSession());
    }

    private void joinSession() {
        String code = sessionCodeField.getValue().trim();
        if (!code.isEmpty()) {
            // Clean up from previous session if any
            if (!sessionCode.isEmpty()) {
                Broadcaster.broadcastPresence(userId, userRole, false, getBaseSessionCode(sessionCode));
                userRegistry.unregisterUserFromSession(userId, getBaseSessionCode(sessionCode));
            }

            this.sessionCode = code;
            this.userRole = code.endsWith("-view") ? "viewer" : "editor";

            // Register with base session code
            String baseSessionCode = getBaseSessionCode(code);
            userRegistry.registerUser(userId, baseSessionCode, userRole);
            editor.setVisible(true);
            editor.setReadOnly("viewer".equals(userRole));
            Broadcaster.broadcastPresence(userId, userRole, true, baseSessionCode);
            Broadcaster.requestDocumentState(userId, baseSessionCode);
            updateUserPanel();
        }
    }

    private String getBaseSessionCode(String code) {
        // Remove -view or -edit suffix if present
        if (code.endsWith("-view") || code.endsWith("-edit")) {
            return code.substring(0, code.lastIndexOf('-'));
        }
        return code;
    }

    private void addEditorStyles() {
        // Add styles to document head
        getElement().executeJs(
                "const style = document.createElement('style');" +
                        "style.textContent = `" +
                        // Remote cursor styles
                        ".remote-cursor {" +
                        "    position: absolute;" +
                        "    height: 20px;" +
                        "    width: 2px;" +
                        "    pointer-events: none;" +
                        "    z-index: 1000;" +
                        "    animation: blink 1s infinite;" +
                        "}" +
                        // Cursor label with user info
                        ".cursor-label {" +
                        "    position: absolute;" +
                        "    top: -18px;" +
                        "    left: 0px;" +
                        "    font-size: 12px;" +
                        "    padding: 0px 4px;" +
                        "    white-space: nowrap;" +
                        "    border-radius: 3px;" +
                        "    color: white;" +
                        "}" +
                        // Text highlighting by author
                        ".text-span {" +
                        "    display: inline;" +
                        "    padding: 0;" +
                        "    margin: 0;" +
                        "    border-radius: 2px;" +
                        "}" +
                        "@keyframes blink {" +
                        "    0%, 100% { opacity: 1; }" +
                        "    50% { opacity: 0.3; }" +
                        "}`;" +
                        "document.head.appendChild(style);");
    }

    private void setupEnhancedCursorTracking() {
        // Add this method to your class
        editor.getElement().executeJs("""
                    this.addEventListener('click', function() {
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                    this.addEventListener('keyup', function(e) {
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                    this.addEventListener('focus', function() {
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                    this.addEventListener('mousemove', function(e) {
                        if (e.buttons === 1) { // Mouse button is pressed
                            this.$server.handleCursorPosition(this.inputElement.selectionStart);
                        }
                    });
                    this.addEventListener('selectionchange', function() {
                        this.$server.handleCursorPosition(this.inputElement.selectionStart);
                    });
                """);
    }

    private void schedulePeriodicCursorUpdates() {
        // Add this method to your class
        getUI().ifPresent(ui -> {
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(2000); // Update every 2 seconds
                        ui.access(() -> {
                            if (!userRole.equals("viewer") && !sessionCode.isEmpty()) {
                                Broadcaster.broadcastCursor(userId, cursorPosition, userColor, sessionCode);
                            }
                            renderRemoteCursors();
                        });
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        });
    }
}
