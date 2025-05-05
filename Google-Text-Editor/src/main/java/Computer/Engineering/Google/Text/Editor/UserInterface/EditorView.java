package Computer.Engineering.Google.Text.Editor.UserInterface;

import Computer.Engineering.Google.Text.Editor.model.Comment;
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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;

import java.util.ArrayList;
import java.util.List;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;

import java.util.UUID;
import java.util.Map;
import elemental.json.JsonObject; // Import JsonObject from Vaadin's elemental.json package
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import com.vaadin.flow.server.VaadinSession;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

@Route("")
@StyleSheet("context://styles/cursor-styles.css")
public class EditorView extends VerticalLayout implements Broadcaster.BroadcastListener {

    // Don't initialize with the shared instance immediately
    private CrdtBuffer crdtBuffer;
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
    private final Map<String, CursorOverlay> cursorOverlays = new ConcurrentHashMap<>();
    private final Div cursorContainer = new Div();

    private Button commentButton;
    private final Map<String, Div> commentMarkers = new HashMap<>();

    private CrdtBuffer getCrdtBuffer() {
        return sessionCode.isEmpty()
                ? new CrdtBuffer("temp")
                : SharedBuffer.getInstance(sessionCode);
    }

    // Then in your constructor:
    public EditorView() {
        // Initialize with a private buffer instance
        this.crdtBuffer = new CrdtBuffer("temp-" + userId);

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
        initializeSessionComponents();
        initializeCommentComponents();

        HorizontalLayout topBanner = new HorizontalLayout(
                new HorizontalLayout(undoButton, redoButton, commentButton), // Add comment button
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

        HorizontalLayout sessionJoinPanel = new HorizontalLayout(sessionCodeField, joinSessionButton);
        sessionJoinPanel.setWidthFull();
        sessionJoinPanel.setAlignItems(FlexComponent.Alignment.BASELINE); // Align items on the same baseline
        sessionJoinPanel.setSpacing(true); // Add spacing between components
        topBanner.add(sessionJoinPanel); // Add to your existing toolbar

        addEditorStyles();
        setupEnhancedCursorTracking();
        schedulePeriodicCursorUpdates();
        setupSelectionTracking();

        editor.getElement().getParent().appendChild(cursorContainer.getElement());
        cursorContainer.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("right", "0")
                .set("bottom", "0")
                .set("pointer-events", "none")
                .set("z-index", "1000");

        addCursorStyles();

        editor.addAttachListener(event -> {
            // Ensure cursor tracking is initialized after the component is attached
            getUI().ifPresent(ui -> ui.access(() -> {
                ui.getPage().executeJs(
                    "console.log('Editor attached, initializing cursors...');"
                );
                setupEnhancedCursorTracking();
                addCursorStyles();
            }));
        });
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
            System.out.println("Rendering remote cursors. Active users: " + userCursors.size());
            
            // Using a self-contained approach that doesn't rely on external function definitions
            editor.getElement().executeJs(
                // Define the cursor creation function and immediately execute it for each cursor
                "(() => {" +
                    // First clean up any old cursors
                    "document.querySelectorAll('.remote-cursor').forEach(el => el.remove());" +
                    
                    // Function to safely create a cursor (defined inside the IIFE)
                    "function createCursorElement(userId, position, color, name, role) {" +
                        "try {" +
                            "const ta = document.querySelector('vaadin-text-area').inputElement;" +
                            "if (!ta) return false;" +
                            
                            // Create cursor element
                            "const cursor = document.createElement('div');" +
                            "cursor.className = 'remote-cursor';" +
                            "cursor.id = 'cursor-' + userId;" +
                            "cursor.style.position = 'absolute';" +
                            "cursor.style.backgroundColor = color;" +
                            "cursor.style.width = '4px';" +
                            "cursor.style.height = '20px';" +
                            "cursor.style.zIndex = '9999';" +
                            "cursor.style.pointerEvents = 'none';" +
                            
                            // Add user label
                            "const label = document.createElement('div');" +
                            "label.className = 'cursor-label';" +
                            "label.style.backgroundColor = color;" +
                            "label.style.color = 'white';" +
                            "label.style.padding = '2px 5px';" +
                            "label.style.borderRadius = '3px';" +
                            "label.style.position = 'absolute';" +
                            "label.style.top = '-20px';" +
                            "label.style.left = '-2px';" +
                            "label.textContent = name + ' (' + role + ')';" +
                            "cursor.appendChild(label);" +
                            
                            // Calculate position
                            "const rect = ta.getBoundingClientRect();" +
                            "const doc = ta.value.substring(0, position);" +
                            "const lines = doc.split('\\n');" +
                            "const lineIdx = lines.length - 1;" +
                            "const lineText = lines[lineIdx];" +
                            "const charWidth = 8;" + 
                            "const lineHeight = 20;" +
                            
                            "const xPos = lineText.length * charWidth + 4;" +
                            "const yPos = lineIdx * lineHeight + 4;" +
                            
                            "cursor.style.left = xPos + 'px';" +
                            "cursor.style.top = yPos + 'px';" +
                            
                            // Add to DOM - directly to document body for better visibility
                            "document.body.appendChild(cursor);" +
                            
                            // Position relative to textarea
                            "const taRect = ta.getBoundingClientRect();" +
                            "cursor.style.position = 'fixed';" +
                            "cursor.style.left = (taRect.left + xPos) + 'px';" +
                            "cursor.style.top = (taRect.top + yPos) + 'px';" +
                            
                            // Add animation
                            "cursor.animate([" +
                                "{opacity: 1}, {opacity: 0.5}, {opacity: 1}" +
                            "], {" +
                                "duration: 1500, iterations: Infinity" +
                            "});" +
                            
                            "console.log('Created cursor for user:', name, 'at position:', position);" +
                            "return true;" +
                        "} catch(e) {" +
                            "console.error('Error creating cursor:', e);" +
                            "return false;" +
                        "}" +
                    "}" +
                    
                    // Return the function for multiple calls
                    "return createCursorElement;" +
                "})()($0, $1, $2, $3, $4)"
            );
            
            // Now create each cursor one by one using separate calls
            userCursors.forEach((id, pos) -> {
                if (!id.equals(userId) && pos >= 0) {
                    String color = userRegistry.getUserColor(id);
                    String role = userRegistry.getUserRole(id);
                    String shortId = id.substring(0, Math.min(id.length(), 6));
                    
                    // Each call is self-contained and doesn't rely on external functions
                    editor.getElement().executeJs(
                        "(() => {" +
                            "try {" +
                                "const ta = document.querySelector('vaadin-text-area').inputElement;" +
                                "if (!ta) return false;" +
                                
                                "const userId = $0;" +
                                "const position = $1;" +
                                "const color = $2;" +
                                "const name = $3;" +
                                "const role = $4;" +
                                
                                // Create cursor with unique ID
                                "const cursor = document.createElement('div');" +
                                "cursor.id = 'cursor-' + userId;" +
                                "cursor.className = 'remote-cursor';" +
                                "cursor.style.position = 'fixed';" +
                                "cursor.style.backgroundColor = color;" +
                                "cursor.style.width = '4px';" +
                                "cursor.style.height = '20px';" +
                                "cursor.style.zIndex = '9999';" +
                                "cursor.style.pointerEvents = 'none';" +
                                "cursor.style.boxShadow = '0 0 5px ' + color;" +
                                
                                // Add user label
                                "const label = document.createElement('div');" +
                                "label.className = 'cursor-label';" +
                                "label.style.backgroundColor = color;" +
                                "label.style.color = 'white';" +
                                "label.style.padding = '2px 5px';" +
                                "label.style.borderRadius = '3px';" +
                                "label.style.position = 'absolute';" +
                                "label.style.top = '-20px';" +
                                "label.style.left = '-2px';" +
                                "label.style.whiteSpace = 'nowrap';" +
                                "label.textContent = name + ' (' + role + ')';" +
                                "cursor.appendChild(label);" +
                                
                                // Calculate position
                                "const rect = ta.getBoundingClientRect();" +
                                "const doc = ta.value.substring(0, position);" +
                                "const lines = doc.split('\\n');" +
                                "const lineIdx = lines.length - 1;" +
                                "const lineText = lines[lineIdx];" +
                                "const charWidth = 8;" + 
                                "const lineHeight = 20;" +
                                
                                // Calculate exact position
                                "const xPos = lineText.length * charWidth;" +
                                "const yPos = lineIdx * lineHeight;" +
                                "cursor.style.left = (rect.left + xPos + 5) + 'px';" +
                                "cursor.style.top = (rect.top + yPos + 5) + 'px';" +
                                
                                // Add directly to body
                                "document.body.appendChild(cursor);" +
                                
                                // Add animation
                                "cursor.animate([" +
                                    "{opacity: 1}, {opacity: 0.5}, {opacity: 1}" +
                                "], {" +
                                    "duration: 1500, iterations: Infinity" +
                                "});" +
                                
                                "console.log('Created cursor for ' + name);" +
                            "} catch(e) {" +
                                "console.error('Error creating cursor:', e);" +
                            "}" +
                        "})();",
                        id, pos, color, shortId, role
                    );
                }
            });
        }));
    }

    // Server push updates (for real-time syncing)
    @Override
    public void receiveBroadcast(List<CrdtNode> incomingNodes, List<CrdtNode> incomingDeleted) {
        getUI().ifPresent(ui -> {
            // Use access() to update UI even in background tabs
            ui.access(() -> {
                // Store cursor position before update
                int cursorPos = getCursorPosition();

                // Update the document
                crdtBuffer.merge(incomingNodes, incomingDeleted);
                String newContent = crdtBuffer.getDocument();
                editor.setValue(newContent);

                // Try to restore cursor position
                editor.getElement().executeJs(
                        "this.inputElement.setSelectionRange($0, $0);",
                        Math.min(cursorPos, newContent.length()));
            });
        });
    }

    // Helper method to get cursor position
    private int getCursorPosition() {
        try {
            // Get selection from the client
            PendingJavaScriptResult result = editor.getElement()
                    .executeJs("return this.inputElement.selectionStart;");
            elemental.json.JsonValue value = result.toCompletableFuture().get(100, TimeUnit.MILLISECONDS);
            return (int) value.asNumber();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void receiveCursor(String remoteUserId, int pos, String color) {
        // Don't process your own cursor updates
        if (!remoteUserId.equals(userId)) {
            getUI().ifPresent(ui -> ui.access(() -> {
                // Store cursor position
                userCursors.put(remoteUserId, pos);
                
                // Log for debugging
                System.out.println("Received cursor update from " + remoteUserId + " at position " + pos);
                
                // Immediately render cursors for faster response
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
        
        // Position cursor container correctly and ensure CSS is loaded
        getUI().ifPresent(ui -> ui.access(() -> {
            // Make sure CSS is injected
            getElement().executeJs(
                "if (!document.getElementById('cursor-styles')) {" +
                    "const style = document.createElement('style');" +
                    "style.id = 'cursor-styles';" +
                    "style.textContent = `" +
                        ".remote-cursor {" +
                        "  position: absolute;" +
                        "  width: 4px;" +
                        "  height: 20px;" +
                        "  z-index: 9999;" +
                        "  pointer-events: none;" +
                        "  animation: cursor-blink 1s infinite;" +
                        "}" +
                        ".cursor-label {" +
                        "  position: absolute;" +
                        "  top: -20px;" +
                        "  left: -2px;" +
                        "  padding: 2px 5px;" +
                        "  border-radius: 3px;" +
                        "  font-size: 12px;" +
                        "  font-weight: bold;" +
                        "  white-space: nowrap;" +
                        "  z-index: 10000;" +
                        "}" +
                        "@keyframes cursor-blink {" +
                        "  0%, 100% { opacity: 1; }" +
                        "  50% { opacity: 0.6; }" +
                        "}" +
                    "`;" +
                    "document.head.appendChild(style);" +
                "}"
            );
            
            // Initialize cursor tracking
            setupEnhancedCursorTracking();
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // First broadcast presence update if in a session
        if (!sessionCode.isEmpty()) {
            String baseSessionCode = getBaseSessionCode(sessionCode);
            Broadcaster.broadcastPresence(userId, userRole, false, baseSessionCode);
            userRegistry.unregisterUserFromSession(userId, baseSessionCode);
        }

        // Then handle local cleanup
        Broadcaster.unregister(this);
        userRegistry.unregisterUser(userId);
        userCursors.remove(userId);

        // No need to update panel for this user since they're leaving
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

            // Switch to the shared buffer for this session
            this.crdtBuffer = SharedBuffer.getInstance(getBaseSessionCode(code));

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

        // Add styles to document head with improved cursor visibility
        getElement().executeJs(
                "const style = document.createElement('style');" +
                        "style.textContent = `" +
                        // Remote cursor styles with enhanced visibility
                        ".remote-cursor {" +
                        "    position: fixed;" +
                        "    height: 20px;" +
                        "    width: 6px;" +  // Increased from 4px to 6px
                        "    pointer-events: none;" +
                        "    z-index: 9999;" + // Very high z-index to ensure visibility
                        "    border-radius: 1px;" +
                        "    animation: cursor-pulse 1s infinite ease-in-out;" +
                        "    box-shadow: 0 0 10px currentColor, 0 0 5px currentColor;" + // Enhanced glow for visibility
                        "}" +
                        ".comment-marker {" +
                        "    position: absolute;" +

                        "    top: -24px;" +
                        "    left: -3px;" +
                        "    font-size: 12px;" +
                        "    padding: 3px 8px;" +
                        "    white-space: nowrap;" +
                        "    border-radius: 3px;" +
                        "    color: white;" +
                        "    font-weight: bold;" +
                        "    z-index: 10000;" + // Even higher z-index for labels
                        "    box-shadow: 0 1px 3px rgba(0,0,0,0.5);" +
                        "}" +
                        // Animation for cursor pulsing
                        "@keyframes cursor-pulse {" +
                        "    0%, 100% { opacity: 1; transform: scale(1); }" +
                        "    50% { opacity: 0.8; transform: scale(1.05); }" +
                        "}`;" +
                        "document.head.appendChild(style);");
        
        // NEW CODE: Apply custom caret color to the editor based on user's assigned color
        editor.getElement().executeJs(
                "this.inputElement.style.caretColor = $0;" +
                // Add a subtle glow effect to the caret
                "this.inputElement.style.textShadow = '0 0 0.5px ' + $0;" +
                "console.log('Applied custom caret color: ' + $0);",
                userColor
        );
    }

    private void setupEnhancedCursorTracking() {
        editor.getElement().executeJs(
            // Use a global registry approach instead of depending on $server
            "window.vaadinCursorTracker = {" +
                "positions: {}, " +
                "sendPosition: function(pos) {" +
                    "if (this.serverConnected) {" +
                        "console.log('Sending cursor position:', pos);" +
                        "this.element.$server.handleCursorPosition(pos);" +
                        "return true;" +
                    "} else {" +
                        "console.log('Server connection not ready yet');" +
                        "return false;" +
                    "}" +
                "}, " +
                "init: function(element) {" +
                    "this.element = element;" +
                    "this.serverConnected = !!element.$server;" +
                    "this.setupEventHandlers();" +
                    "this.startConnectionCheck();" +
                "}," +
                "setupEventHandlers: function() {" +
                    "if (!this.element.inputElement) {" +
                        "setTimeout(() => this.setupEventHandlers(), 200);" +
                        "return;" +
                    "}" +
                    
                    "const tracker = this;" +
                    "const input = this.element.inputElement;" +
                    
                    "const sendPos = function() {" +
                        "tracker.sendPosition(input.selectionStart);" +
                    "};" +
                    
                    "input.addEventListener('click', sendPos);" +
                    "input.addEventListener('keyup', sendPos);" +
                    "input.addEventListener('mouseup', sendPos);" +
                    
                    "input.addEventListener('keydown', function(e) {" +
                        "if (e.key.startsWith('Arrow') || e.key === 'Home' || e.key === 'End') {" +
                            "setTimeout(sendPos, 10);" +
                        "}" +
                    "});" +
                    
                    "input.addEventListener('focus', sendPos);" +
                    
                    "console.log('Event handlers registered');" +
                "}," +
                "startConnectionCheck: function() {" +
                    "const tracker = this;" +
                    "const checkConnection = function() {" +
                        "tracker.serverConnected = !!tracker.element.$server;" +
                        "if (tracker.serverConnected) {" +
                            "console.log('Server connection established');" +
                        "} else {" +
                            "console.log('Waiting for server connection...');" +
                            "setTimeout(checkConnection, 500);" +
                        "}" +
                    "};" +
                    "checkConnection();" +
                    
                    "// Also set up a periodic position sender" +
                    "setInterval(() => {" +
                        "if (document.activeElement === tracker.element.inputElement) {" +
                            "tracker.sendPosition(tracker.element.inputElement.selectionStart);" +
                        "}" +
                    "}, 2000);" +
                "}" +
            "};" +
            "window.vaadinCursorTracker.init(this);"
        );

        // Add this at the end of the method to create a custom cursor for the current user
        editor.getElement().executeJs(
            "(() => {" +
                "const createLocalCursor = () => {" +
                    "// Remove any existing local cursor" +
                    "const existingCursor = document.getElementById('local-cursor-indicator');" +
                    "if (existingCursor) existingCursor.remove();" +
                    
                    "// Create local cursor element" +
                    "const cursor = document.createElement('div');" +
                    "cursor.id = 'local-cursor-indicator';" +
                    "cursor.style.position = 'absolute';" +
                    "cursor.style.width = '6px';" +
                    "cursor.style.height = '20px';" +
                    "cursor.style.backgroundColor = $0;" + // User's color
                    "cursor.style.opacity = '0.7';" +
                    "cursor.style.pointerEvents = 'none';" +
                    "cursor.style.zIndex = '9998';" + // Below remote cursors
                    "cursor.style.boxShadow = '0 0 8px ' + $0;" +
                    "cursor.style.transition = 'left 0.1s, top 0.1s';" +
                    
                    "// Add to DOM" +
                    "document.body.appendChild(cursor);" +
                    
                    "// Add a label with user name (you)" +
                    "const label = document.createElement('div');" +
                    "label.textContent = 'You';" +
                    "label.style.position = 'absolute';" +
                    "label.style.top = '-20px';" +
                    "label.style.left = '0';" +
                    "label.style.backgroundColor = $0;" +
                    "label.style.color = 'white';" +
                    "label.style.padding = '2px 5px';" +
                    "label.style.borderRadius = '3px';" +
                    "label.style.fontSize = '12px';" +
                    "label.style.fontWeight = 'bold';" +
                    "label.style.whiteSpace = 'nowrap';" +
                    "cursor.appendChild(label);" +
                    
                    "return cursor;" +
                "};" +
                
                "// Create the cursor" +
                "const localCursor = createLocalCursor();" +
                
                "// Function to update its position" + 
                "const updatePosition = () => {" +
                    "if (!this.inputElement) return;" +
                    
                    "const ta = this.inputElement;" +
                    "const pos = ta.selectionStart;" +
                    "const rect = ta.getBoundingClientRect();" +
                    
                    "const text = ta.value.substring(0, pos);" +
                    "const lines = text.split('\\n');" +
                    "const lineIdx = lines.length - 1;" +
                    "const lineText = lines[lineIdx];" +
                    "const charWidth = 8;" + 
                    "const lineHeight = 20;" +
                    
                    "const xPos = lineText.length * charWidth + 4;" +
                    "const yPos = lineIdx * lineHeight + 4;" +
                    
                    "localCursor.style.left = (rect.left + xPos) + 'px';" +
                    "localCursor.style.top = (rect.top + yPos) + 'px';" +
                "};" +
                
                "// Update position on various events" + 
                "this.inputElement.addEventListener('click', updatePosition);" +
                "this.inputElement.addEventListener('keyup', updatePosition);" +
                "this.inputElement.addEventListener('mouseup', updatePosition);" +
                "this.inputElement.addEventListener('focus', updatePosition);" +
                
                "// Initial position update" +
                "setTimeout(updatePosition, 100);" +
                
                "// Periodic updates" +
                "setInterval(updatePosition, 500);" +
            "})();",
            userColor
        );
    }

    private void schedulePeriodicCursorUpdates() {
        getUI().ifPresent(ui -> {
            ui.setPollInterval(500); // Poll every 500ms to keep UI responsive
            
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(800); // Update every 800ms 
                        ui.access(() -> {
                            if (!userRole.equals("viewer") && !sessionCode.isEmpty()) {
                                // Re-broadcast your cursor position regularly
                                Broadcaster.broadcastCursor(userId, cursorPosition, userColor, sessionCode);
                                
                                // Always re-render cursors to ensure they stay visible
                                renderRemoteCursors();
                            }
                        });
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        });
    }

    private void initializeCommentComponents() {
        commentButton = new Button("Add Comment", e -> addComment());
        commentButton.setEnabled(false);
    }

    private void addComment() {
        // Get selected text range
        PendingJavaScriptResult js = editor.getElement().executeJs(
                "return {start: this.inputElement.selectionStart, end: this.inputElement.selectionEnd}");

        js.then(JsonObject.class, range -> {
            int start = (int) range.getNumber("start");
            int end = (int) range.getNumber("end");

            if (start < end) {
                // Show comment dialog
                TextField commentField = new TextField("Comment");
                commentField.setWidth("300px");

                Dialog dialog = new Dialog();
                dialog.add(new Span("Add comment to selected text:"));
                dialog.add(commentField);

                Button saveButton = new Button("Save Comment", saveEvent -> {
                    String commentText = commentField.getValue();
                    if (!commentText.isEmpty()) {
                        Comment comment = crdtBuffer.addComment(userId, userColor, commentText, start, end);
                        Broadcaster.broadcastComment(comment, sessionCode);
                        renderComments();
                        dialog.close();
                    }
                });

                dialog.add(new HorizontalLayout(saveButton,
                        new Button("Cancel", cancelEvent -> dialog.close())));
                dialog.open();
            }
        });
    }

    // Check for text selection to enable/disable comment button
    private void setupSelectionTracking() {
        editor.getElement().executeJs(
                """
                        (function() {
                            // Wait for the input element to be initialized
                            const checkInputElement = () => {
                                const inputElement = this.querySelector('textarea');

                                if (inputElement) {
                                    console.log('Input element found!');

                                    function checkSelection() {
                                        const start = inputElement.selectionStart;
                                        const end = inputElement.selectionEnd;
                                        const hasSelection = start !== end;

                                        if (hasSelection) {
                                            console.log('Selection detected: ' + start + ' to ' + end);
                                        }

                                        // Use the component's server connection
                                        this.$server.updateSelectionState(hasSelection);
                                    }

                                    // Add event listeners to the actual textarea element
                                    ['mouseup', 'keyup', 'click'].forEach(eventName => {
                                        inputElement.addEventListener(eventName, () => checkSelection());
                                    });

                                    // Initial check
                                    checkSelection();
                                } else {
                                    console.log('Input element not found, retrying...');
                                    setTimeout(checkInputElement, 100); // retry in 100ms
                                }
                            };

                            // Start checking for input element
                            checkInputElement();
                        })();
                        """);
    }

    @ClientCallable
    private void updateSelectionState(boolean hasSelection) {
        System.out.println("Selection state changed: " + hasSelection + ", Role: " + userRole);

        // Ensure commentButton exists and userRole is initialized
        if (commentButton != null && userRole != null) {
            commentButton.setEnabled(hasSelection && !userRole.equals("viewer"));
            System.out.println("Comment button enabled: " + commentButton.isEnabled());
        } else {
            System.out.println("Button or role not initialized yet");
        }
    }

    // Render all comments as markers
    private void renderComments() {
        // Remove existing markers
        commentMarkers.values().forEach(div -> div.getElement().removeFromParent());
        commentMarkers.clear();

        // Get editor dimensions for positioning
        PendingJavaScriptResult js = editor.getElement().executeJs("""
                    return {
                        rect: this.inputElement.getBoundingClientRect(),
                        lineHeight: parseInt(window.getComputedStyle(this.inputElement).lineHeight) || 20
                    }
                """);

        js.then(JsonObject.class, info -> {
            JsonObject rect = info.getObject("rect");
            int lineHeight = (int) info.getNumber("lineHeight");
            int editorLeft = (int) rect.getNumber("left");
            int editorTop = (int) rect.getNumber("top");

            // Create marker for each comment
            for (Comment comment : crdtBuffer.getComments()) {
                createCommentMarker(comment, editorLeft, editorTop, lineHeight);
            }
        });
    }

    private void createCommentMarker(Comment comment, int editorLeft, int editorTop, int lineHeight) {
        // Get position coordinates
        editor.getElement().executeJs("""
                    (function() {
                        const ta = this.inputElement;
                        const text = ta.value;
                        const pos = $0;

                        // Create a mirror to calculate position
                        const mirror = document.createElement('div');
                        mirror.style.position = 'absolute';
                        mirror.style.top = '0';
                        mirror.style.left = '0';
                        mirror.style.visibility = 'hidden';
                        mirror.style.whiteSpace = 'pre-wrap';
                        mirror.style.wordWrap = 'break-word';
                        mirror.style.font = window.getComputedStyle(ta).font;
                        mirror.style.padding = window.getComputedStyle(ta).padding;

                        // Add content up to position
                        mirror.textContent = text.substring(0, pos);

                        // Add a marker where comment would be
                        const span = document.createElement('span');
                        span.textContent = '.';
                        mirror.appendChild(span);

                        document.body.appendChild(mirror);
                        const rect = span.getBoundingClientRect();
                        document.body.removeChild(mirror);

                        return {
                            left: rect.left,
                            top: rect.top
                        };
                    })()
                """, comment.getStartPosition()).then(JsonObject.class, coords -> {
            int left = (int) coords.getNumber("left");
            int top = (int) coords.getNumber("top");

            // Create comment marker
            Div marker = new Div();
            marker.addClassName("comment-marker");
            marker.getStyle()
                    .set("position", "absolute")
                    .set("left", (left - editorLeft) + "px")
                    .set("top", (top - editorTop) + "px")
                    .set("width", "8px")
                    .set("height", "8px")
                    .set("border-radius", "50%")
                    .set("background-color", comment.getAuthorColor())
                    .set("cursor", "pointer")
                    .set("z-index", "1000");

            // Add tooltip with comment content
            marker.getElement().setAttribute("title",
                    comment.getAuthorId().substring(0, 6) + ": " + comment.getContent());

            // Add click handler to show comment detail
            marker.addClickListener(e -> {
                Dialog dialog = new Dialog();
                dialog.add(new H3("Comment"));
                dialog.add(new Span("By: " + comment.getAuthorId().substring(0, 6)));
                dialog.add(new Paragraph(comment.getContent()));
                dialog.add(new Button("Close", ce -> dialog.close()));
                dialog.open();
            });

            // Add to document
            getElement().appendChild(marker.getElement());
            commentMarkers.put(comment.getCommentId(), marker);
        });
    }

    // Handle receiving comments from other users
    @Override
    public void receiveComment(Comment comment) {
        getUI().ifPresent(ui -> ui.access(() -> {
            crdtBuffer.addExistingComment(comment); // Use proper method
            renderComments();
        }));

    private void addCursorStyles() {
        getElement().executeJs(
            "const style = document.createElement('style');" +
            "style.id = 'cursor-styles-direct';" +
            "style.textContent = `" +
                ".remote-cursor {" +
                "  position: fixed;" + // Use fixed positioning for viewport coordinates
                "  width: 4px;" +
                "  height: 20px;" +
                "  z-index: 9999;" +
                "  pointer-events: none;" +
                "  animation: cursor-blink 1s infinite;" +
                "  box-shadow: 0 0 8px currentColor;" + // Add glow effect
                "}" +
                ".cursor-label {" +
                "  position: absolute;" +
                "  top: -20px;" +
                "  left: 0;" +
                "  background-color: inherit;" +
                "  color: white;" +
                "  padding: 2px 5px;" +
                "  border-radius: 3px;" +
                "  font-size: 12px;" +
                "  font-weight: bold;" + // Make text bold
                "  white-space: nowrap;" +
                "  z-index: 10000;" +
                "  box-shadow: 0 1px 4px rgba(0,0,0,0.4);" + // Add shadow
                "}" +
                "@keyframes cursor-blink {" +
                "  0%, 100% { opacity: 1; }" +
                "  50% { opacity: 0.6; }" +
                "}" +
            "`;" +
            "document.head.appendChild(style);"
        );

    }
}
