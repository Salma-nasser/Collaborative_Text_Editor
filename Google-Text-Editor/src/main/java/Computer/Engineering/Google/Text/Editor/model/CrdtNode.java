package Computer.Engineering.Google.Text.Editor.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CrdtNode implements Comparable<CrdtNode> { 
    private final String siteId;
    private final int clock;
    private final String parentId;
    private final int counter;
    private final char value;
    private boolean deleted;


    public CrdtNode(String siteId, int clock, int counter, String parentId ,char value) {
        
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

    public String getParentId(){
        return parentId;
    }

    public void markDeleted() {
        this.deleted = true;
    }
    public char getCharValue(){
        return value;
    }

    public String getSiteId(){
        return siteId;
    }
    public int getClock(){
        return clock;
    }
    @Override
    public int compareTo(CrdtNode other) {
        if (this.parentId.equals(other.parentId)) {
            if (this.counter != other.counter) {
                return Integer.compare(other.counter, this.counter); // Descending order
            }
            return this.siteId.compareTo(other.siteId);
        }
        return this.parentId.compareTo(other.parentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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

