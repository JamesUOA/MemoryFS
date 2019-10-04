import com.github.lukethompsxn.edufuse.filesystem.FileSystemStub;
import com.github.lukethompsxn.edufuse.struct.*;
import com.github.lukethompsxn.edufuse.util.ErrorCodes;
import com.github.lukethompsxn.edufuse.util.FuseFillDir;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import util.MemoryINode;
import util.MemoryINodeDir;
import util.MemoryINodeTable;
import util.MemoryVisualiser;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import com.sun.security.auth.module.UnixSystem;

/**
 * @author Luke Thompson and (James Zhang)
 * @since 04.09.19
 */
public class MemoryFS extends FileSystemStub {
    private static final String HELLO_PATH = "/hello";
    private static final String HELLO_STR = "Hello World!\n";


    private MemoryINodeDir root = new MemoryINodeDir();
    private MemoryVisualiser visualiser;
    private UnixSystem unix = new UnixSystem();


    @Override
    public Pointer init(Pointer conn) {

        // setup an example file for testing purposes
        MemoryINode iNode = new MemoryINode();
        FileStat stat = createFileStat(HELLO_PATH.getBytes().length,FileStat.S_IFREG | 0444 | 0200, 0);
        // you will have to add more stat information here eventually
        iNode.setStat(stat);
        iNode.setContent(HELLO_STR.getBytes());
        root.updateINode(HELLO_PATH, iNode);

        if (isVisualised()) {
            visualiser = new MemoryVisualiser();
            visualiser.sendINodeTable(root.getiNodeTable());
        }

        return conn;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        MemoryINodeTable table = getParentDir(path);
        if (Objects.equals(path, "/")) { // minimal set up for the mount point root
            stat.st_mode.set(FileStat.S_IFDIR | 0755);
            stat.st_nlink.set(2);
        } else if (table.containsINode(path)) {
            FileStat savedStat = table.getINode(path).getStat();
            // fill in the stat object with values from the savedStat object of your inode
            stat.st_mode.set(savedStat.st_mode.intValue());
            stat.st_size.set(savedStat.st_size.intValue());

            // setting GID and UID
            stat.st_gid.set(unix.getGid());
            stat.st_uid.set(unix.getUid());

            stat.st_nlink.set(savedStat.st_nlink.intValue());


            // time
            stat.st_atim.tv_sec.set(savedStat.st_atim.tv_sec.get());
            stat.st_atim.tv_nsec.set(savedStat.st_atim.tv_nsec.intValue());

            stat.st_atim.tv_sec.set(savedStat.st_atim.tv_sec.get());
            stat.st_atim.tv_nsec.set(savedStat.st_atim.tv_nsec.intValue());

            stat.st_ctim.tv_sec.set(savedStat.st_ctim.tv_sec.get());
            stat.st_ctim.tv_nsec.set(savedStat.st_ctim.tv_nsec.intValue());

        } else {
            res = -ErrorCodes.ENOENT();
        }
        return res;
    }
    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
        // For each file in the directory call filler.apply.
        // The filler.apply method adds information on the files
        // in the directory, it has the following parameters:
        //      buf - a pointer to a buffer for the directory entries
        //      name - the file name (with no "/" at the beginning)
        //      stbuf - the FileStat information for the file
        //      off - just
        MemoryINodeTable table = getParentDir(path);
        for(String node:table.entries()){
            boolean is_valid = true;
            String[] nodeSplit = node.split("(?<=/)");
            String[] pathSplit = path.split("(?<=/)");

            for(int i=0;i<pathSplit.length;i++){
                if ((!nodeSplit[i].equals(pathSplit[i])) ||
                        nodeSplit.length <= pathSplit.length ||
                        nodeSplit.length > (pathSplit.length + 1)) {
                    is_valid = false;
                    break;
                }
            }

            if(is_valid){
                MemoryINode iNode = table.getINode(node);
                filler.apply(buf, node.substring(node.lastIndexOf('/') + 1), iNode.getStat(), 0);
            }

        }
        filler.apply(buf,".",null,0);
        filler.apply(buf,"..",null,0);
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        MemoryINodeTable table = getParentDir(path);
        if (!table.containsINode(path)) {
            return -ErrorCodes.ENOENT();
        }
        // you need to extract data from the content field of the inode and place it in the buffer
        // something like:
        // buf.put(0, content, offset, amount);

        MemoryINode node = table.getINode(path);
        byte[] content = node.getContent();
        int amount = content.length - (int) offset;

        FileStat stat = node.getStat();

        Instant instant = Instant.now();
        stat.st_atim.tv_sec.set(instant.getEpochSecond());
        stat.st_atim.tv_nsec.set(instant.getNano());

        buf.put(0,content,(int)offset,amount);

        if (isVisualised()) {
            visualiser.sendINodeTable(table);
        }

        return amount;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        MemoryINodeTable table = getParentDir(path);
        if (!table.containsINode(path)) {
            return -ErrorCodes.ENOENT(); // ENONET();
        }
        // similar to read but you get data from the buffer like:
        // buf.get(0, content, offset, size);


        MemoryINode node = table.getINode(path);
        byte[] content = node.getContent();

        node.setContent(java.util.Arrays.copyOf(content, node.getContent().length+(int)size));
        FileStat stat = node.getStat();
        Instant instant = Instant.now();
        stat.st_mtim.tv_sec.set(instant.getEpochSecond());
        stat.st_mtim.tv_nsec.set(instant.getNano());

        node.getStat().st_size.set((int) offset + size);
        buf.get(0, node.getContent(), (int) offset, (int) size);

        if (isVisualised()) {
            visualiser.sendINodeTable(table);
        }

        return (int) size;
    }

    private FileStat createFileStat(int contentLength, long mode, long rdev){

        FileStat stat = new FileStat(Runtime.getSystemRuntime());

        stat.st_mode.set(mode);
        stat.st_dev.set(rdev);
        stat.st_size.set(contentLength);
        stat.st_nlink.set(1);

        Instant instant = Instant.now();

        //modification time
        stat.st_mtim.tv_sec.set(instant.getEpochSecond());
        stat.st_mtim.tv_nsec.set(instant.getNano());
        //access time
        stat.st_atim.tv_sec.set(instant.getEpochSecond());
        stat.st_atim.tv_nsec.set(instant.getNano());
        //change time
        stat.st_ctim.tv_sec.set(instant.getEpochSecond());
        stat.st_ctim.tv_nsec.set(instant.getNano());
        return stat;
    }

    @Override
    public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
        MemoryINodeTable table = getParentDir(path);
        if (table.containsINode(path)) {
            return -ErrorCodes.EEXIST();
        }

        MemoryINode mockINode = new MemoryINode();
        // set up the stat information for this inode

        FileStat stat = createFileStat(mockINode.getContent().length, mode, rdev);
        mockINode.setStat(stat);
        table.updateINode(path, mockINode);

        if (isVisualised()) {
            visualiser.sendINodeTable(table);
        }

        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return super.statfs(path, stbuf);
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        // The Timespec array has the following information.
        // You need to set the corresponding fields of the inode's stat object.
        // You can access the data in the Timespec objects with "get()" and "longValue()".
        // You have to find out which time fields these correspond to.
        // timespec[0].tv_nsec
        // timespec[0].tv_sec
        // timespec[1].tv_nsec
        // timespec[1].tv_sec
        return 0;
    }
    
    @Override
    public int link(java.lang.String oldpath, java.lang.String newpath) {
        MemoryINodeTable table = getParentDir(oldpath);

        if (!table.containsINode(oldpath) && !table.containsINode(newpath)) {
            return -ErrorCodes.ENOENT(); // ENONET();
        }

        MemoryINode node = table.getINode(oldpath);

        FileStat stat = node.getStat();
        stat.st_nlink.set(stat.st_nlink.intValue() + 1);
        table.updateINode(newpath,node);
        return 0;
    }

    @Override
    public int unlink(String path) {
        MemoryINodeTable table = getParentDir(path);
        if (!table.containsINode(path)) {
            return -ErrorCodes.ENONET();
        }
        // delete the file if there are no more hard links
        MemoryINode node = table.getINode(path);
        FileStat stat = node.getStat();

        stat.st_nlink.set(stat.st_nlink.intValue() - 1 );
        table.removeINode(path);

        return 0;
    }

    private MemoryINodeTable getParentDir(String path){
        String[] items = path.split("/");

        List<String> strList = new ArrayList<>();
        for (int i=0; i<items.length-1;i++){
            if(!items[i].equals("")){
                strList.add(items[i]);
            }
        }
        MemoryINodeDir node;
        MemoryINodeTable table = root.getiNodeTable();
        StringBuilder currentPath = new StringBuilder();
        for (int i =0; i<strList.size() -1 ; i++){
            currentPath.append("/").append(strList.get(i));
            node = (MemoryINodeDir) table.getINode(currentPath.toString());
            table = node.getiNodeTable();

        }
        return table;
    }

    @Override
    public int mkdir(String path, long mode) {
        MemoryINodeTable table = getParentDir(path);
        if (table.containsINode(path)) {
            return -ErrorCodes.EEXIST();
        }
        MemoryINode node = new MemoryINodeDir();
        FileStat stat = createFileStat(node.getContent().length, mode|FileStat.S_IFDIR, 0);
        node.setStat(stat);

        table.updateINode(path,node);

        return 0;
    }

    @Override
    public int rmdir(String path) {
        return 0;
    }

    @Override
    public int rename(String oldpath, String newpath) {
        return 0;
    }

    @Override
    public int truncate(String path, @size_t long size) {
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, @size_t long size, int flags) {
        return 0;
    }

    @Override
    public int getxattr(String path, String name, Pointer value, @size_t long size) {
        return 0;
    }

    @Override
    public int listxattr(String path, Pointer list, @size_t long size) {
        return 0;
    }

    @Override
    public int removexattr(String path, String name) {
        return 0;
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public void destroy(Pointer initResult) {
        if (isVisualised()) {
            try {
                visualiser.stopConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int access(String path, int mask) {
        return 0;
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        return 0;
    }

    public static void main(String[] args) {
        MemoryFS fs = new MemoryFS();
        try {
            fs.mount(args, true);
        } finally {
            fs.unmount();
        }
    }
}
