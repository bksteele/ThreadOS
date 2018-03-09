public class FileSystem {

    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    //constructor
    public FileSystem(int diskBlocks) {

        superblock = new SuperBlock(diskBlocks);
        directory = new Directory(superblock.inodeBlocks);
        filetable = new FileTable(directory);

        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }

        close(dirEnt);
    }

    //Formats the disk (Disk.java's data contents). The parameter
    //files specifies the maximum number of files to be created (the
    //number of inodes to be allocated) in your file system. The
    //return value is 0 on success, otherwise -1.
    public int format(int files) {

        //check for a system of at least 1 file
        if (files <= 0)
            return -1;

        superblock.format(files);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);

        return 0;
    }

    //Opens the file specified by the fileName string in the given
    //mode.  The call allocates a new file
    //descriptor, fd to this file. The file is created if it does not
    //exist in the mode "w", "w+" or "a".
    public int open(String fileName, String mode) {

        //Checks for proper name, mode, making sure filetable isn't full
        if (fileName == null || fileName == "")
            return -1;
        if (mode == null || mode == "")
            return -1;
        if (this.filetable.table.size() == 32)
            return -1;


        //check to see if file  in w/w+/a
        short ref = directory.namei(fileName);
        if (directory.fileNames[ref].equals(fileName)) {
            for (int i = 0; i < filetable.table.size(); i++) {
                if (filetable.table.get(i).iNumber == ref) {
                    //SysLib.open must return a negative number as an
                    //error value if the file does not exist in
                    //the mode "r"
                    if (filetable.table.get(i).mode == "r")
                        return -1;
                    //else, allocate the space and open it up
                    else {
                        filetable.table.get(i).mode = mode;
                        filetable.falloc(fileName, mode);
                        return 0;
                    }
                }
            }
        }
        //if it's not in the table, it's cool just make it
        filetable.falloc(fileName, mode);
        return 0;
    }

    //Reads up to buffer.length bytes from the file indicated by fd,
    //starting at the position currently pointed to by the seek pointer.
    public int read(int fd, byte buffer[]) {
        FileTableEntry temp = filetable.table.get(fd);

        //mode is w/a return -1 for error
        if (temp.mode == "w" || temp.mode == "a")
            return -1;

        synchronized (temp) {
            if (buffer.length == 0 && buffer == null)
                return -1;

            int buffSize = buffer.length;
            int fileSize = fsize(temp.iNumber);
            int bRead = 0;

            //If bytes remaining between the current seek pointer and the end
            //of file are less than buffer.length, SysLib.read reads as many
            //bytes as possible, putting them into the beginning of buffer.
            int bID = temp.inode.getBlockID(temp.seekPtr);

            while (temp.seekPtr < fileSize && (buffSize > 0)) {

                byte[] data = new byte[Disk.blockSize];
                SysLib.rawread(bID, data);

                //increments the seek pointer by the number of bytes to have
                //been read
                int start = temp.seekPtr % Disk.blockSize;
                int blocksLeft = Disk.blockSize - start;
                int fileLeft = fsize(fd) - temp.seekPtr;
                int smallestLeft = Math.min(blocksLeft, fileLeft);
                smallestLeft = Math.min(smallestLeft, buffSize);

                System.arraycopy(blocksLeft, start, buffer, bRead, smallestLeft);
                bRead += smallestLeft;
                temp.seekPtr += smallestLeft;
                buffSize -= smallestLeft;
            }
            //return the number of bytes that have been read
            return bID;
        }
    }

    //Writes the contents of buffer to the file indicated by fd, starting
    //at the position indicated by the seek pointer. The operation may
    //overwrite existing data in the file and/or append to the end of the file.
    //SysLib.write increments the seek pointer by the number of bytes to have
    //been written. The return value is the number of bytes that have been
    //written, or a negative value upon an error.
    public int write(int fd, byte buffer[]){

        FileTableEntry temp = (FileTableEntry)filetable.table.get(fd);

        //new thread is using file, increment count
        filetable.table.elementAt(fd).count++;

        //check for read only and invalid files
        if(temp.mode == "r" || temp == null)
            return -1;
        //check for bad inputs
        if (buffer.length == 0 || buffer == null)
            return -1;

        int bWritten = 0;
        int bLeft = buffer.length;

        synchronized (temp) {

            //write should delete all blocks, then write from beginning
            if(temp.mode == "w"){
                temp.inode.direct = new short[11];
                temp.seekPtr = 0;
                while(bLeft > 0){

                    SysLib.rawwrite(temp.seekPtr, buffer);

                }
                return bWritten;
            }

            //w+ and a work the same, just add to end of file

            return bWritten;
        }
    }

    //Updates the seek pointer corresponding to fd
    //The offset can be positive or negative.
    //The offset location of the seek pointer in the file is
    //returned from the call to seek.
    public int seek(int fd, int offset, int whence){

        FileTableEntry temp = (FileTableEntry)filetable.table.get(fd);

        if(temp == null)
            return -1;

        int ptr = temp.seekPtr;

        //If whence is SEEK_SET (= 0), the file's seek pointer is
        //set to offset bytes from the beginning of the file
        if(whence == SEEK_SET)
            ptr = offset;

        //If whence is SEEK_CUR (= 1), the file's seek pointer is
        //set to its current value plus the offset.
        if(whence == SEEK_CUR)
            ptr = ptr + offset;

        //If whence is SEEK_END (= 2), the file's seek pointer is
        //set to the size of the file plus the offset.
        if(whence == SEEK_END)
            ptr = temp.inode.length + offset;

        //If the user attempts to set the seek pointer to a
        //negative number you must clamp it to zero.
        if(ptr < 0)
            ptr = 0;

        //If the user attempts to set the pointer to beyond the file size,
        //you must set the seek pointer to the end of the file.
        if(ptr > temp.inode.length)
            ptr = temp.inode.length;

        filetable.table.elementAt(fd).seekPtr = ptr;
        return 0;
    }

    //Closes the file corresponding to fd, commits all file
    //transactions on this file, and unregisters fd from the user
    //file descriptor table of the calling thread's TCB. The return
    //value is 0 in success, otherwise -1.
    public int close(int fd) {
        FileTableEntry temp = (FileTableEntry)filetable.table.get(fd);
        if (temp == null)
            return -1;

        synchronized (temp) {

            temp.count--;
            if (temp.count == 0)
                return filetable.ffree(filetable.table.elementAt(fd));

            return 0;
        }
    }

    //Deletes the file specified by fileName.
    //All blocks used by file are freed. If the file is currently
    //open, it is not deleted and the operation returns a -1. If
    // successfully deleted a 0 is returned.
    public synchronized int delete(String fileName) {

        if (directory.fileNames)
            if (directory.ifree(filetable.table.get(iNumber)) && (close(temp.iNumber) == 0)
        return 0;

        return -1;

    }

    //Returns the size in bytes of the file indicated by fd.
    public synchronized int fsize(int fd) {
        return filetable.table.get(fd).inode.length;
    }


}
