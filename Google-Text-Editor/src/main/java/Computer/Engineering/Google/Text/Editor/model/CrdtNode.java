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
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @Override
    public int compareTo(CrdtNode other) {
        // First compare by parentId (group siblings)
        int parentCompare = this.parentId.compareTo(other.parentId);
        if (parentCompare != 0) {
            return parentCompare;
        }

        // Then compare by counter (sequence number), descending (higher first)
        int counterCompare = Integer.compare(other.counter, this.counter);
        if (counterCompare != 0) {
            return counterCompare;
        }

        // Then compare by siteId (to break ties deterministically)
        int siteCompare = this.siteId.compareTo(other.siteId);
        if (siteCompare != 0) {
            return siteCompare;
        }

        // Finally, compare by clock (as a last resort)
        return Integer.compare(this.clock, other.clock);
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