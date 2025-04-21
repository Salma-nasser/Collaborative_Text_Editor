package Computer.Engineering.Google.Text.Editor.model;

import java.util.*;

public class CrdtBuffer {
    private List<CrdtNode> nodes;
    private String siteId;
    private int clock;
    private Set<String> deletedSet;

    public CrdtBuffer(String siteId) {
        this.siteId = siteId;
        this.clock = 0;
        this.nodes = new ArrayList<>();
        this.deletedSet = new HashSet<>();
    }

    // basic case
    /*
     * parent is my previous char
     * clock accor to user end count
     * counter 
     */ 

    // Insert character
    public void insert(char charValue, String parentId) {
        clock++;
        int counter = 0; // Default counter for new inserts

        // Find correct counter value for same parent
        // site id is hal 2na nfs el45s ely 2bly wla 
        for (CrdtNode node : nodes) {
            if (node.getParentId().equals(parentId)) {
                counter = counter + 1;
            }
        }

        CrdtNode newNode = new CrdtNode(siteId, clock, counter, parentId, charValue);
        nodes.add(newNode);
        Collections.sort(nodes); // Maintain order
        System.out.println("countet: "+counter);
    }
    // in printing on editor check if it deleted so it is not printed
    // Delete character by marking it as a tombstone
    public void delete(String siteId , int clock) {
        for (CrdtNode node : nodes) {
            if (node.getSiteId()==(siteId) && node.getClock()==(clock)) {
                System.out.println("deleted");
                node.markDeleted();
                deletedSet.add(siteId);
                break;
            }
        }
    }

    // // Merge incoming changes from another peer
    // public void merge(List<CrdtNode> incomingNodes, Set<String> incomingDeleted) {
    //     for (CrdtNode node : incomingNodes) {
    //         if (!nodes.contains(node)) {
    //             nodes.add(node);
    //         }
    //     }
    //     deletedSet.addAll(incomingDeleted);
    //     Collections.sort(nodes);
    // }

    //Get document as a string (ignoring deleted nodes)
    public String getDocument() {
        StringBuilder doc = new StringBuilder();
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()) {
                doc.append(node.getCharValue());
            }
        }
        return doc.toString();
    }

    public void printBuffer() {
        for (CrdtNode node : nodes) {
            if (!node.isDeleted()){
                System.out.println(node.getCharValue());
            }
        }  
    }

    public static void main(String[] args) {
        CrdtBuffer buffer = new CrdtBuffer("site1");
        buffer.insert('H', "0");
        buffer.insert('e', "1");
        buffer.insert('l', "2");
        buffer.insert('l', "3");
        buffer.insert('o', "4");
        

        buffer.insert('m',"4");
        buffer.printBuffer();
        buffer.delete("site1",1);

        buffer.printBuffer();
        //System.out.println(buffer.getDocument()); // Should print "Hello"
    }
}
// test cases delete and insert
// undo and redo 
