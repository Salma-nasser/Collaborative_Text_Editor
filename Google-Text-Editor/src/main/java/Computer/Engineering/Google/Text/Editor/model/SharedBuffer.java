package Computer.Engineering.Google.Text.Editor.model;

public class SharedBuffer {
  private static final CrdtBuffer INSTANCE = new CrdtBuffer("shared");

  private SharedBuffer() {
  }

  public static CrdtBuffer getInstance() {
    return INSTANCE;
  }
}