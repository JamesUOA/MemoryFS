package util;

import com.github.lukethompsxn.edufuse.struct.FileStat;

import java.util.Set;

public class MemoryINodeDir extends MemoryINode{

    private MemoryINodeTable  iNodeTable = new MemoryINodeTable();

    public MemoryINodeDir(){
        super();
    }

    public MemoryINodeTable getiNodeTable() {

        FileStat stat = this.getStat();
        return iNodeTable;
    }


    public MemoryINode getINode(String path) {
        return iNodeTable.getINode(path);
    }

    public void updateINode(String path, MemoryINode iNode) {
        iNodeTable.updateINode(path, iNode);
    }

    public boolean containsINode(String path) {
        return iNodeTable.containsINode(path);
    }

    public Set<String> entries() {
        return iNodeTable.entries();
    }

    public void removeINode(String path) {
        iNodeTable.removeINode(path);
    }
}
