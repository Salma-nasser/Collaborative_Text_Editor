package Computer.Engineering.Google.Text.Editor.UserInterface;

import Computer.Engineering.Google.Text.Editor.model.CrdtBuffer;
import Computer.Engineering.Google.Text.Editor.model.CrdtNode;
import Computer.Engineering.Google.Text.Editor.model.SharedBuffer;
import Computer.Engineering.Google.Text.Editor.sync.Broadcaster;
import Computer.Engineering.Google.Text.Editor.services.UserRegistry;
import Computer.Engineering.Google.Text.Editor.model.Comment;

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
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.dialog.Dialog;

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
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import elemental.json.JsonObject;
import elemental.json.JsonValue;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.Comparator;

@Route("")
@StyleSheet("context://styles/cursor-styles.css")
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
    private final Map<String, CursorOverlay> cursorOverlays = new ConcurrentHashMap<>();
    private final Div cursorContainer = new Div();
    private Button commentButton;
    private final VerticalLayout commentsPanel = new VerticalLayout();

    private CrdtBuffer getCrdtBuffer() {
        return sessionCode.isEmpty()
                ? new CrdtBuffer("temp")
                : SharedBuffer.getInstance(sessionCode);
    }

    public EditorView() {
        // First initialize editor
        editor = new TextArea();
        editor.setWidthFull();
        editor.setHeight("600px");
        editor.setValueChangeMode(ValueChangeMode.EAGER);
        // Add any other editor configuration...

        // Now it's safe to call methods that use editor
        initializeSessionComponents();
        initializeCommentComponents();

        // And you can add listeners to editor
        editor.getElement().executeJs("""
            this.addEventListener('click', function() {
                console.log('Cursor click: ' + this.inputElement.selectionStart);
                this.$server.handleCursorPosition(this.inputElement.selectionStart);
            });
            // Rest of your event listeners...
        """);

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
                new HorizontalLayout(commentButton), // Add comment button in the middle
                new HorizontalLayout(importUpload, exportButton));
        topBanner.setWidthFull();
        topBanner.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topBanner.setAlignItems(FlexComponent.Alignment.CENTER);
        topBanner.getStyle().set("background-color", "#f0f0f0").set("padding", "10px");

        // Editor Setup: TextArea with CRDT logic for typing
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

            // Create final copies of variables for use in lambdas
            final int finalStart = start;
            final int finalEndOld = endOld;
            final int finalEndNew = endNew;

            // Improved deletion logic
            if (finalEndOld >= finalStart) {
                System.out.println("Deleting characters from position " + finalStart + " to " + finalEndOld);
                
                // First, identify all specific nodes that need deletion
                List<String> nodesToDelete = new ArrayList<>();
                for (int pos = finalStart; pos <= finalEndOld; pos++) {
                    String nodeId = crdtBuffer.getNodeIdAtPosition(pos);
                    if (!nodeId.equals("0")) {
                        nodesToDelete.add(nodeId);
                        System.out.println("Marking node for deletion: " + nodeId + " at position " + pos);
                    }
                }
                
                // Check for comments that should be deleted
                List<Comment> commentsToDelete = new ArrayList<>();
                for (Comment comment : crdtBuffer.getComments()) {
                    // If the comment's entire text range is within the deleted range, or if the
                    // comment start is in the deletion range, mark it for removal
                    if ((comment.getStartPosition() >= finalStart && comment.getEndPosition() <= finalEndOld) ||
                        (comment.getStartPosition() >= finalStart && comment.getStartPosition() <= finalEndOld)) {
                        commentsToDelete.add(comment);
                        System.out.println("Marking comment for deletion: " + comment.getCommentId() + " at position " + 
                                        comment.getStartPosition() + "-" + comment.getEndPosition());
                    }
                }
                
                // Then delete nodes one by one
                for (String nodeId : nodesToDelete) {
                    String[] parts = nodeId.split("-");
                    System.out.println("Deleting node: " + nodeId);
                    crdtBuffer.delete(parts[0], Integer.parseInt(parts[1]));
                }
                
                // Delete the affected comments
                for (Comment comment : commentsToDelete) {
                    crdtBuffer.removeComment(comment.getCommentId());
                    // Notify others that the comment has been removed
                    Broadcaster.broadcastCommentRemoval(comment.getCommentId(), sessionCode);
                    System.out.println("Deleted comment: " + comment.getCommentId());
                }
                
                // Update the comments panel if any comments were deleted
                if (!commentsToDelete.isEmpty()) {
                    updateCommentsPanel();
                }
            }

            // If there are insertions
            if (finalStart <= finalEndNew) {
                // Use the actual cursor position to determine the parent node
                // Get the actual cursor position from the browser, not the inferred insertion point
                PendingJavaScriptResult posResult = editor.getElement().executeJs(
                    "return this.inputElement.selectionStart;"
                );
                
                posResult.then(Integer.class, actualPos -> {
                    System.out.println("Actual cursor position: " + actualPos);
                    
                    // Calculate the parent node id for insertion
                    int insertPos = Math.min(finalStart, actualPos);
                    String baseParentId;
                    
                    if (insertPos == 0) {
                        baseParentId = "0"; // Root node
                    } else {
                        // Get the node right before the insertion point
                        baseParentId = crdtBuffer.getNodeIdAtPosition(insertPos - 1);
                        System.out.println("Using base parent ID: " + baseParentId + " for insertion");
                    }
                    
                    // Use a temporary local variable to track parent ID through the closure
                    final String[] currentParentId = {baseParentId};
                    final String finalNewText = newText;
                    
                    // Insert each character with the correct parent ID chain
                    for (int i = finalStart; i <= finalEndNew; i++) {
                        final int index = i; // To use in lambda
                        char c = finalNewText.charAt(i);
                        
                        // Important: Insert synchronously to maintain order
                        String newNodeId = crdtBuffer.insertAndReturnId(c, currentParentId[0]);
                        currentParentId[0] = newNodeId; // Update parent for next character
                        
                        System.out.println("Inserted '" + c + "' with parent: " + 
                                          (index == finalStart ? baseParentId : "previous node") + 
                                          ", new node ID: " + newNodeId);
                    }
                    
                    // Broadcast the changes immediately
                    Broadcaster.broadcast(
                        new ArrayList<>(crdtBuffer.getAllNodes()),
                        new ArrayList<>(crdtBuffer.getDeletedNodes()), sessionCode);
                });
            }

            Broadcaster.broadcast(
                    new ArrayList<>(crdtBuffer.getAllNodes()),
                    new ArrayList<>(crdtBuffer.getDeletedNodes()), sessionCode);

            // Debug: print the resulting document and CRDT tree
            System.out.println("Final document: '" + crdtBuffer.getDocument() + "'");
            crdtBuffer.printBuffer();
        });

        // Add this after creating the editor in your constructor
        editor.getElement().addEventListener("mouseup", e -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                editor.getElement().executeJs(
                    "return {start: this.inputElement.selectionStart, end: this.inputElement.selectionEnd}"
                ).then(JsonObject.class, range -> {
                    int start = (int) range.getNumber("start");
                    int end = (int) range.getNumber("end");
                    boolean hasSelection = start != end;
                    
                    System.out.println("Direct selection handler: " + start + " to " + end + 
                                    " (hasSelection=" + hasSelection + ")");
                    updateSelectionState(hasSelection);
                });
            }));
        });

        // userColor is already initialized during declaration

        userPanel.setWidth("200px");
        userPanel.getStyle().set("background-color", "#f9f9f9");
        updateUserPanel();

        // Initialize comments panel
        commentsPanel.setWidth("250px");
        commentsPanel.getStyle()
            .set("background-color", "#f9f9f9")
            .set("border-left", "1px solid #ddd")
            .set("padding", "10px")
            .set("overflow-y", "auto");
        commentsPanel.add(new H3("Comments"));

        // Update the layout to include comments panel
        HorizontalLayout mainArea = new HorizontalLayout(userPanel, editor, commentsPanel);
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
        topBanner.add(sessionJoinPanel); // Add to your existing toolbar

        addEditorStyles();
        setupEnhancedCursorTracking();
        setupSelectionTracking();
        schedulePeriodicCursorUpdates();

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

    @ClientCallable
    private void updateSelectionState(boolean hasSelection) {
        System.out.println("Selection state changed: " + hasSelection + ", Role: " + userRole);

        // Always enable the comment button when there's a selection and the user is not a viewer
        if (commentButton != null) {
            boolean shouldEnable = hasSelection && !"viewer".equals(userRole);
            commentButton.setEnabled(shouldEnable);
            
            System.out.println("Comment button enabled: " + shouldEnable + 
                               " (hasSelection=" + hasSelection + 
                               ", role=" + userRole + ")");
            
            // Force UI update
            getUI().ifPresent(ui -> ui.access(() -> {
                commentButton.getElement().setProperty("disabled", !shouldEnable);
                commentButton.getStyle().set("opacity", shouldEnable ? "1" : "0.5");
                
                // Visual indicator for debug purposes
                if (shouldEnable) {
                    commentButton.getStyle().set("background-color", "#4CAF50");
                } else {
                    commentButton.getStyle().set("background-color", "");
                }
            }));
        } else {
            System.err.println("Comment button is null, cannot update state");
        }
    }
    
    private void initializeSessionComponents() {
        sessionCodeField = new TextField("Session Code");
        joinSessionButton = new Button("Join Session", e -> joinSession());
    }

    private void initializeCommentComponents() {
        commentButton = new Button("Add Comment", e -> addComment());
        commentButton.setEnabled(false);
        commentButton.setId("comment-button"); // Add an ID for easier reference
        
        // Add styles to make the disabled state more visible
        commentButton.getStyle().set("opacity", "0.5");
        commentButton.getStyle().set("transition", "opacity 0.3s, background-color 0.3s");
        
        // REPLACE the addClickListener with this element event listener
        editor.getElement().addEventListener("click", e -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                // Get current selection state directly
                PendingJavaScriptResult js = editor.getElement().executeJs(
                    "return {start: this.inputElement.selectionStart, end: this.inputElement.selectionEnd}");
                    
                js.then(JsonObject.class, range -> {
                    int start = (int) range.getNumber("start");
                    int end = (int) range.getNumber("end");
                    boolean hasSelection = start != end;
                        
                    // Update button state
                    updateSelectionState(hasSelection);
                });
            }));
        });
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
                        // Cursor label with user info
                        ".cursor-label {" +
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

    private void setupSelectionTracking() {
        editor.getElement().executeJs(
            "(() => {" +
            "  const element = this;" +
            "  let lastSelectionState = false;" +
            "  " +
            "  function waitForElements() {" +
            "    if (!element.inputElement) {" +
            "      console.log('Waiting for input element to initialize...');" +
            "      setTimeout(waitForElements, 200);" +
            "      return;" +
            "    }" +
            "    " +
            "    const inputElement = element.inputElement;" +
            "    console.log('Input element ready for selection tracking');" +
            "    " +
            "    // Check selection and notify server if changed" +
            "    function notifySelectionChange() {" +
            "      const start = inputElement.selectionStart;" +
            "      const end = inputElement.selectionEnd;" +
            "      const hasSelection = start !== end;" +
            "      " +
            "      // Only send updates when state changes to avoid flooding" +
            "      if (hasSelection !== lastSelectionState) {" +
            "        lastSelectionState = hasSelection;" +
            "        console.log('Selection changed: ' + (hasSelection ? 'text selected' : 'no selection'));" +
            "        " +
            "        // Try to notify server, with retries" +
            "        function tryNotifyServer(attempts) {" +
            "          if (attempts <= 0) return;" +
            "          " +
            "          if (element.$server) {" +
            "            try {" +
            "              element.$server.updateSelectionState(hasSelection);" +
            "              console.log('Server notified of selection state: ' + hasSelection);" +
            "            } catch (e) {" +
            "              console.error('Failed to notify server: ', e);" +
            "              setTimeout(() => tryNotifyServer(attempts - 1), 100);" +
            "            }" +
            "          } else {" +
            "            console.log('Server not available, retrying in 200ms...');" +
            "            setTimeout(() => tryNotifyServer(attempts - 1), 200);" +
            "          }" +
            "        }" +
            "        " +
            "        // Try with 5 attempts" +
            "        tryNotifyServer(5);" +
            "      }" +
            "    }" +
            "    " +
            "    // Direct event handlers for selection changes" +
            "    ['mouseup', 'keyup', 'selectionchange'].forEach(eventType => {" +
            "      inputElement.addEventListener(eventType, () => {" +
            "        notifySelectionChange();" +
            "      });" +
            "    });" +
            "    " +
            "    // For browsers that support the selectionchange event at document level" +
            "    document.addEventListener('selectionchange', () => {" +
            "      if (document.activeElement === inputElement) {" +
            "        notifySelectionChange();" +
            "      }" +
            "    });" +
            "    " +
            "    // Also run selection check on mouse down/up" +
            "    inputElement.addEventListener('mousedown', () => {" +
            "      // Check after a small delay to allow selection to complete" +
            "      setTimeout(notifySelectionChange, 10);" +
            "    });" +
            "    " +
            "    // Initial check and periodic checks" +
            "    setTimeout(notifySelectionChange, 500);" +
            "    setInterval(notifySelectionChange, 1000); // Backup check every second" +
            "  }" +
            "  " +
            "  // Start watching" +
            "  waitForElements();" +
            "})();"
        );
    }

    private void addComment() {
        // Define the js variable first
        PendingJavaScriptResult js = editor.getElement().executeJs(
            "return {start: this.inputElement.selectionStart, end: this.inputElement.selectionEnd}");
        
        // Then use it
        js.then(JsonObject.class, range -> {
            final int selStart = (int) range.getNumber("start");
            final int selEnd = (int) range.getNumber("end");
    
            if (selStart < selEnd) {
                // Show comment dialog
                TextField commentField = new TextField("Comment");
                commentField.setWidth("300px");
    
                Dialog dialog = new Dialog();
                dialog.add(new Span("Add comment to selected text:"));
                dialog.add(commentField);
    
                Button saveButton = new Button("Save Comment", saveEvent -> {
                    String commentText = commentField.getValue();
                    if (!commentText.isEmpty()) {
                        Comment comment = crdtBuffer.addComment(userId, userColor, commentText, selStart, selEnd);
                        Broadcaster.broadcastComment(comment, sessionCode);
                        updateCommentsPanel();
                        dialog.close();
                    }
                });
    
                dialog.add(new HorizontalLayout(saveButton,
                        new Button("Cancel", cancelEvent -> dialog.close())));
                dialog.open();
            }
        });
    }

    @Override
    public void receiveComment(Comment comment) {
        getUI().ifPresent(ui -> ui.access(() -> {
            crdtBuffer.addExistingComment(comment);
            updateCommentsPanel();
        }));
    }

    @Override
    public void receiveCommentRemoval(String commentId) {
        getUI().ifPresent(ui -> ui.access(() -> {
            crdtBuffer.removeComment(commentId);
            updateCommentsPanel();
        }));
    }

    private void updateCommentsPanel() {
        // Clear all existing comments from the UI panel
        commentsPanel.removeAll();
        commentsPanel.add(new H3("Comments (" + crdtBuffer.getComments().size() + ")"));
        
        // Get all comments and sort them by position in the document
        List<Comment> sortedComments = new ArrayList<>(crdtBuffer.getComments());
        sortedComments.sort(Comparator.comparingInt(Comment::getStartPosition));
        
        // Add each comment to the panel
        for (Comment comment : sortedComments) {
            // Create a card for each comment
            Div card = new Div();
            card.getStyle()
                .set("background-color", "white")
                .set("border-radius", "4px")
                .set("padding", "10px")
                .set("margin-bottom", "10px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)")
                .set("border-left", "4px solid " + comment.getAuthorColor());
            
            // Author info
            Span author = new Span(comment.getAuthorId().substring(0, 6));
            author.getStyle()
                .set("font-weight", "bold")
                .set("color", comment.getAuthorColor());
            
            // Comment text
            Paragraph content = new Paragraph(comment.getContent());
            content.getStyle().set("margin", "5px 0");
            
            // Commented text preview
            String documentText = crdtBuffer.getDocument();
            String commentedText = "";
            int start = comment.getStartPosition();
            int end = comment.getEndPosition();
            if (start >= 0 && end <= documentText.length() && start < end) {
                commentedText = documentText.substring(start, end);
            }
            
            Div textPreview = new Div();
            textPreview.getStyle()
                .set("background-color", "#f0f0f0")
                .set("padding", "5px")
                .set("border-radius", "3px")
                .set("margin", "5px 0")
                .set("font-family", "monospace")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
            textPreview.setText("\"" + commentedText + "\"");
            
            // Jump to comment button
            Button jumpButton = new Button("Jump to", e -> {
                editor.focus();
                // Select the commented text
                editor.getElement().executeJs(
                    "this.inputElement.setSelectionRange($0, $1); this.inputElement.focus();",
                    comment.getStartPosition(), comment.getEndPosition()
                );
            });
            jumpButton.getStyle().set("margin-top", "5px");
            
            // Add all components to the card
            card.add(author, content, textPreview, jumpButton);
            commentsPanel.add(card);
        }
        
        // Add a message if there are no comments
        if (sortedComments.isEmpty()) {
            Span noComments = new Span("No comments yet. Select text and use the Comment button to add comments.");
            noComments.getStyle().set("font-style", "italic");
            commentsPanel.add(noComments);
        }
    }
}