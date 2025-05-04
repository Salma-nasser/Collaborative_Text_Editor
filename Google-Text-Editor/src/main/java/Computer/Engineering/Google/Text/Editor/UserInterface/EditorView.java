package Computer.Engineering.Google.Text.Editor.UserInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import Computer.Engineering.Google.Text.Editor.model.CrdtBuffer;
import Computer.Engineering.Google.Text.Editor.model.CrdtNode;
import Computer.Engineering.Google.Text.Editor.model.SharedBuffer;
import Computer.Engineering.Google.Text.Editor.services.UserRegistry;
import Computer.Engineering.Google.Text.Editor.sync.Broadcaster;

@Route("")
public class EditorView extends VerticalLayout implements Broadcaster.BroadcastListener {

    private CrdtBuffer getCrdtBuffer() {
        return sessionCode.isEmpty() 
            ? new CrdtBuffer("temp") 
            : SharedBuffer.getInstance(sessionCode);
    }
    private TextArea editor;
    private int cursorPosition = 0;
    private final VerticalLayout userPanel = new VerticalLayout();
    private final UserRegistry userRegistry = UserRegistry.getInstance();
    private final String userId = UUID.randomUUID().toString();
    private final String userColor = UserRegistry.getInstance().registerUser(userId, "", "editor"); // Initialize with empty session
    private final Map<String, Integer> userCursors = new HashMap<>();
    private final Map<String, String> userRoles = new HashMap<>();

    private TextField sessionCodeField;
    private Button joinSessionButton;
    private String userRole = "editor";
    private String sessionCode = "";

    public EditorView() {
        addCursorStyles();
        initializeEditor();
        initializeSessionComponents();
        initializeToolbar();
        setupMainLayout();
    }

    private void initializeEditor() {
        editor = new TextArea();
        editor.setWidthFull();
        editor.setHeightFull();
        editor.getStyle().set("resize", "none");
        editor.setReadOnly(false);
        editor.setVisible(false);

        setupEditorListeners();
    }

    private void setupEditorListeners() {
        // Cursor position tracking
        editor.getElement().addEventListener("input", e -> updateCursorPosition());
        editor.getElement().addEventListener("keydown", e -> updateCursorPosition());
        editor.getElement().addEventListener("click", e -> updateCursorPosition());

        // Text change handling
        editor.addValueChangeListener(event -> {
            if (!event.isFromClient()) return;
            handleTextChange(event.getValue(), getCrdtBuffer().getDocument());
        });
    }

   private void updateCursorPosition() {
    PendingJavaScriptResult js = editor.getElement().executeJs(
        "return this.inputElement.selectionStart;");
    js.then(Integer.class, pos -> {
        cursorPosition = pos;
        Broadcaster.broadcastCursor(userId, cursorPosition, userColor, sessionCode); // Added sessionCode
    });
}

    private void handleTextChange(String newText, String oldText) {
        int oldLen = oldText.length();
        int newLen = newText.length();
        CrdtBuffer buffer = SharedBuffer.getInstance(sessionCode);
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
            String nodeIdToDelete = getCrdtBuffer().getNodeIdAtPosition(start);
            if (!nodeIdToDelete.equals("0")) {
                String[] parts = nodeIdToDelete.split("-");
                getCrdtBuffer().delete(parts[0], Integer.parseInt(parts[1]));
            }
        }

        // Handle insertions
        int numInserted = endNew - start + 1;
        int insertPos = Math.max(cursorPosition - numInserted, 0);
        String parentId = getCrdtBuffer().getNodeIdAtPosition(insertPos - 1);

        for (int i = start; i <= endNew; i++) {
            char c = newText.charAt(i);
           getCrdtBuffer().insert(c, parentId == null ? "0" : parentId);
            insertPos++;
            parentId =getCrdtBuffer().getNodeIdAtPosition(insertPos - 1);
        }

        Broadcaster.broadcast(
            new ArrayList<>(getCrdtBuffer().getAllNodes()),
            new ArrayList<>(getCrdtBuffer().getDeletedNodes()),
            sessionCode  );
    }

    private void initializeSessionComponents() {
        sessionCodeField = new TextField("Session Code");
        joinSessionButton = new Button("Join Session", e -> joinSession());
        VaadinSession.getCurrent().setAttribute("userId", userId);
    }

    private void initializeToolbar() {
        // Import setup
        MemoryBuffer buffer = new MemoryBuffer();
        Upload importUpload = new Upload(buffer);
        importUpload.setAcceptedFileTypes(".txt");
        importUpload.setUploadButton(new Button("Import"));
        importUpload.setDropAllowed(false);
        importUpload.addSucceededListener(event -> handleFileImport(buffer));

        // Export setup
        Anchor exportAnchor = new Anchor();
        exportAnchor.getElement().setAttribute("download", true);
        add(exportAnchor);
        exportAnchor.setVisible(false);

        Button exportButton = new Button("Export", e -> handleFileExport(exportAnchor));

        HorizontalLayout topBanner = new HorizontalLayout(
            new HorizontalLayout(new Button("Undo"), new Button("Redo")),
            new HorizontalLayout(importUpload, exportButton));
        
        topBanner.setWidthFull();
        topBanner.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topBanner.setAlignItems(FlexComponent.Alignment.CENTER);
        topBanner.getStyle().set("background-color", "#f0f0f0").set("padding", "10px");

        HorizontalLayout sessionJoinPanel = new HorizontalLayout(sessionCodeField, joinSessionButton);
        sessionJoinPanel.setWidthFull();
        topBanner.add(sessionJoinPanel);

        add(topBanner);
    }

    private void handleFileImport(MemoryBuffer buffer) {
        try {
            String content = new String(buffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
           getCrdtBuffer().clear();
            String parentId = "0";
            for (char c : content.toCharArray()) {
                getCrdtBuffer().insert(c, parentId);
                parentId = getCrdtBuffer().getNodeIdAtPosition(getCrdtBuffer().getDocument().length() - 1);
            }
            editor.setValue(content);
            Broadcaster.broadcast(
                new ArrayList<>(getCrdtBuffer().getAllNodes()),
                new ArrayList<>(getCrdtBuffer().getDeletedNodes()),
                sessionCode  );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileExport(Anchor exportAnchor) {
        String content = editor.getValue();
        StreamResource resource = new StreamResource("document.txt",
            () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        resource.setContentType("text/plain");
        exportAnchor.setHref(resource);
        exportAnchor.setVisible(true);
        exportAnchor.getElement().callJsFunction("click");
    }

    private void setupMainLayout() {
        userPanel.setWidth("200px");
        userPanel.getStyle().set("background-color", "#f9f9f9");
        updateUserPanel();

        HorizontalLayout mainArea = new HorizontalLayout(userPanel, editor);
        mainArea.setSizeFull();
        mainArea.setFlexGrow(1, editor);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);
        add(mainArea);
        expand(mainArea);
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
        
        // Register with base session code (without -view suffix)
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
            .set("color", info.getColor())  // Use getter
            .set("font-weight", "bold");
        
        Span roleSpan = new Span(info.getRole());
        roleSpan.getStyle()
            .set("font-style", "italic")
            .set("margin-left", "5px");
        
        userEntry.add(userIdSpan, roleSpan);
        userPanel.add(userEntry);
    });
}
        

    private void addCursorStyles() {
        getElement().executeJs(
            "const style = document.createElement('style');" +
            "style.textContent = `" +
            ".remote-cursor {" +
            "    position: absolute;" +
            "    height: 1.2em;" +
            "    width: 2px;" +
            "    pointer-events: none;" +
            "    animation: blink 1s step-end infinite;" +
            "}" +
            "@keyframes blink {" +
            "    from, to { opacity: 1; }" +
            "    50% { opacity: 0.5; }" +
            "}`;" +
            "document.head.appendChild(style);");
    }

    private void renderRemoteCursors() {
        getUI().ifPresent(ui -> ui.access(() -> {
            editor.getElement().executeJs(
                "Array.from(document.querySelectorAll('.remote-cursor')).forEach(e => e.remove());");
            
            userCursors.forEach((id, pos) -> {
                if (!id.equals(userId)) {
                   String color = userRegistry.getUserColor(id);
                    String role = userRegistry.getUserRole(id);
                    String shortId = id.substring(0, 8);
                    
                    editor.getElement().executeJs(
                        "const ta = this.inputElement;" +
                        "if (ta && ta.value) {" +
                        "   const pos = Math.min($0, ta.value.length);" +
                        "   ta.focus();" +
                        "   ta.setSelectionRange(pos, pos);" +
                        "   const rect = ta.getBoundingClientRect();" +
                        "   const cursor = document.createElement('div');" +
                        "   cursor.className = 'remote-cursor';" +
                        "   cursor.style.position = 'absolute';" +
                        "   cursor.style.height = (rect.bottom - rect.top) + 'px';" +
                        "   cursor.style.width = '2px';" +
                        "   cursor.style.background = $1;" +
                        "   cursor.style.left = rect.left + 'px';" +
                        "   cursor.style.top = rect.top + 'px';" +
                        "   cursor.style.pointerEvents = 'none';" +
                        "   cursor.style.zIndex = '100';" +
                        "   const tooltip = document.createElement('div');" +
                        "   tooltip.textContent = $2 + ' (' + $3 + ')';" +
                        "   tooltip.style.position = 'absolute';" +
                        "   tooltip.style.left = '5px';" +
                        "   tooltip.style.bottom = '100%';" +
                        "   tooltip.style.background = 'white';" +
                        "   tooltip.style.padding = '2px 5px';" +
                        "   tooltip.style.border = '1px solid #ccc';" +
                        "   tooltip.style.borderRadius = '3px';" +
                        "   tooltip.style.fontSize = '12px';" +
                        "   tooltip.style.color = $1;" +
                        "   tooltip.style.display = 'none';" +
                        "   cursor.appendChild(tooltip);" +
                        "   cursor.onmouseover = () => tooltip.style.display = 'block';" +
                        "   cursor.onmouseout = () => tooltip.style.display = 'none';" +
                        "   document.body.appendChild(cursor);" +
                        "}", pos, color, shortId, role);
                }
            });
        }));
    }

    // BroadcastListener implementations
    @Override
    public void receiveBroadcast(List<CrdtNode> incomingNodes, List<CrdtNode> incomingDeleted) {
        getUI().ifPresent(ui -> ui.access(() -> {
            CrdtBuffer buffer = SharedBuffer.getInstance(sessionCode);
            buffer.merge(incomingNodes, incomingDeleted);
            editor.setValue(buffer.getDocument());
            getCrdtBuffer().merge(incomingNodes, incomingDeleted);
            String doc = getCrdtBuffer().getDocument();
            editor.setValue(doc);
            editor.getElement().executeJs(
                "this.inputElement.setSelectionRange($0, $0);", 
                Math.min(cursorPosition, doc.length()));
        }));
    }

    @Override
public void receiveCursor(String remoteUserId, int pos, String color) {
    if (!remoteUserId.equals(userId)) {
        userCursors.put(remoteUserId, pos);
        userRegistry.registerUser(remoteUserId, sessionCode, userRegistry.getUserRole(remoteUserId));
        updateUserPanel();
        renderRemoteCursors();
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
    if (!this.sessionCode.equals(sessionCode)) return;
    
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
    userRegistry.registerUser(userId, sessionCode, userRole);
    Broadcaster.broadcastPresence(userId, userRole, true, sessionCode);
}

  @Override
protected void onDetach(DetachEvent detachEvent) {
    Broadcaster.unregister(this);
    userRegistry.unregisterUserFromSession(userId, sessionCode);
    userCursors.remove(userId);
    Broadcaster.broadcastCursor(userId, -1, userColor, sessionCode);
    Broadcaster.broadcastPresence(userId, userRole, false, sessionCode);
    updateUserPanel();
}
}