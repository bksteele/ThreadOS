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

//                      UNFINISHED

    //Opens the file specified by the fileName string in the given
    //mode.  The call allocates a new file
    //descriptor, fd to this file. The file is created if it does not
    //exist in the mode "w", "w+" or "a". SysLib.open must return a
    //negative number as an error value if the file does not exist in
    //the mode "r". If the calling thread's user file descriptor
    //table is full, SysLib.open should return an error value. The seek
    //pointer is initialized to zero in the mode "r", "w", and "w+",
    // whereas initialized at the end of the file in the mode "a".
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
                    if (filetable.table.get(i).mode == "r")
                        return -1;
                    else {
                        filetable.table.get(i).mode = mode;
                        filetable.falloc(fileName, mode);
                        return 0;
                    }
                }
            }
        }
        filetable.falloc(fileName, mode);
        return 0;
    }

    //Reads up to buffer.length bytes from the file indicated by fd,
    //starting at the position currently pointed to by the seek pointer.
    public int read(int fd, byte buffer[]) {
        FileTableEntry temp = filetable.table.get();

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
                int fileLeft = fsize(temp) - temp.seekPtr;
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
    int write(int fd, byte buffer[]);

    //Updates the seek pointer corresponding to fd as follows:
    //If whence is SEEK_SET (= 0), the file's seek pointer is
    //set to offset bytes from the beginning of the file
    //If whence is SEEK_CUR (= 1), the file's seek pointer is
    //set to its current value plus the offset.
    //The offset can be positive or negative.
    //If whence is SEEK_END (= 2), the file's seek pointer is
    //set to the size of the file plus the offset.
    //The offset can be positive or negative.
    //If the user attempts to set the seek pointer to a
    //negative number you must clamp it to zero. If the
    //user attempts to set the pointer to beyond the file size,
    //you must set the seek pointer to the end of the file.
    //The offset location of the seek pointer in the file is
    //returned from the call to seek.
    int seek(int fd, int offset, int whence);

    //Closes the file corresponding to fd, commits all file
    //transactions on this file, and unregisters fd from the user
    //file descriptor table of the calling thread's TCB. The return
    //value is 0 in success, otherwise -1.
    public int close(int fd) {
        FileTableEntry temp = filetable.table[fd];
        if (temp == null)
            return -1;

        synchronized (temp) {

            temp.count--;
            if (temp.count == 0)
                return filetable.ffree(filetable.table[fd]);

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
        return filetable.table.get(fd).length;
    }


}
