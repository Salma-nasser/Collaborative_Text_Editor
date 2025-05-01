package Computer.Engineering.Google.Text.Editor.model;

import java.util.Objects;

public class CrdtNode implements Comparable<CrdtNode> {
    private String siteId;
    private int clock;
    private int counter;
    private String parentId;
    private char value;
    private boolean deleted;

    public CrdtNode(String siteId, int clock, int counter, String parentId, char value) {

        this.siteId = siteId;
        this.clock = clock;
        this.parentId = parentId;
        this.counter = counter;
        this.value = value;
        this.deleted = false;

    }

    public String getUniqueId() {
        return siteId + "-" + clock;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getParentId() {
        return parentId;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public char getCharValue() {
        return value;
    }

    public String getSiteId() {
        return siteId;
    }

    public int getClock() {
        return clock;
    }

    public int getCounter() {
        return counter;
    }

    @Override
    public int compareTo(CrdtNode other) {
        // First compare by clock (to ensure chronological order)
        int clockCompare = Integer.compare(this.clock, other.clock);
        if (clockCompare != 0) {
            return clockCompare;
        }

        // Then compare by parentId
        int parentCompare = this.parentId.compareTo(other.parentId);
        if (parentCompare != 0) {
            return parentCompare;
        }

        // Finally compare by counter (ascending order for same parent)
        return Integer.compare(this.counter, other.counter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CrdtNode crdtNode = (CrdtNode) o;
        return clock == crdtNode.clock && siteId.equals(crdtNode.siteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteId, clock);
    }

    @Override
    public String toString() {
        return (deleted ? "⌫" : value) + "(" + getUniqueId() + ")";
    }
}