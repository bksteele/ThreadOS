public class FileSystem {

    private Superblock superblock;
    private Directory directory;
    private FileTable filetable;

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    public final static int SUCCESS = 0;
    public final static int ERROR = -1;

    //constructor
    public FileSystem(int diskBlocks) {

        superblock = new Superblock(diskBlocks);


        directory = new Directory(superblock.getTotalINodes());
        filetable = new FileTable(directory);

        //Open base
        FileTableEntry entry = open("/", Mode.READ_ONLY);
        int dSize = fileSize(entry);
        if (dSize > 0) {
            byte[] dirData = new byte[dSize];
            read(entry, dirData);
            directory.bytes2directory(dirData);
        }

        close(entry);
    }

    //Formats the disk (Disk.java's data contents). The parameter
    //files specifies the maximum number of files to be created (the
    //number of inodes to be allocated) in your file system. The
    //return value is 0 on success, otherwise -1.
    public int format(int files) {
        // check for a system of at least 1 file, and make sure that our
        // that our file table is empty before formatting
        if (files > 0 && filetable.fempty()) {
            SysLib.cerr("inside format in FileSystem");
            superblock.format(files);
            return SUCCESS;
        }

        return ERROR;
    }


    /**
     * Opens the file specified by the fileName string in the given
     * mode.  The call allocates a new file descriptor, fd to this file.
     * The file is created if it does not exist in the mode "w", "w+" or "a".
     *
     * @param fileName of the file we want to open
     * @param mode     that we want to open the file in
     * @return null if the mode was invalid, new {@link FileTableEntry} with the
     * given fileName and mode otherwise.
     */
    public FileTableEntry open(String fileName, String mode) {
        // create a new entry
        FileTableEntry entry = filetable.falloc(fileName, mode);

        // we were able to make a new entry
        if (entry != null) {
            switch (entry.mode) {
                case Mode.APPEND:
                    entry.seekPtr = this.fileSize(entry);
                    break;

                // cases are all handed the same way but distinguished for
                // clarity. NOTE the fall through.
                case Mode.WRITE_ONLY:
                    // fall through
                case Mode.READ_ONLY:
                    // fall through
                case Mode.READ_WRITE:
                    entry.seekPtr = FileSystemHelper.BEGINNING_OF_FILE;
                    break;

                default:
                    // was an unrecognized or invalid mode
                    return null;
            }
        }

        return entry;
    }

    //Reads up to buffer.length bytes from the file indicated by fd,
    //starting at the position currently pointed to by the seek pointer.
    public int read(FileTableEntry entry, byte buffer[]) {

        //mode is w/a return -1 for error
        if (entry.mode.equals(Mode.WRITE_ONLY) || entry.mode.equals(Mode.APPEND))
            return -1;

        synchronized (entry) {
            if (buffer == null || buffer.length == 0)
                return FileSystemHelper.INVALID;

            int buffSize = buffer.length;
            int fileSize = fileSize(entry);
            int bRead = 0;

            //If bytes remaining between the current seek pointer and the end
            //of file are less than buffer.length, SysLib.read reads as many
            //bytes as possible, putting them into the beginning of buffer.
            int bID = FileSystemHelper.calculateBlockNumber(entry.seekPtr);

            while (entry.seekPtr < fileSize && (buffSize > 0)) {

                byte[] data = new byte[Disk.blockSize];
                SysLib.rawread(bID, data);

                //increments the seek pointer by the number of bytes to have
                //been read
                int start = entry.seekPtr % Disk.blockSize;
                int blocksLeft = Disk.blockSize - start;
                int fileLeft = fileSize(entry) - entry.seekPtr;
                int smallestLeft = Math.min(blocksLeft, fileLeft);
                smallestLeft = Math.min(smallestLeft, buffSize);

                System.arraycopy(blocksLeft, start, buffer, bRead, smallestLeft);
                bRead += smallestLeft;
                entry.seekPtr += smallestLeft;
                buffSize -= smallestLeft;
            }
            //return the number of bytes that have been read
            return bID;
        }
    }


    public int write(FileTableEntry entry, byte buffer[]) {
        if (entry == null || entry.mode.equals(Mode.READ_ONLY)) {
            return FileSystemHelper.INVALID;
        }
        if(entry.inode.flag == 4)
            return FileSystemHelper.INVALID;

        int bytesInBuffer = buffer.length;
        int writtenBytes = 0;
        byte data[] = new byte[Disk.blockSize];

        // while we still have data to write
        while (bytesInBuffer > 0) {
            short blockNumber = entry.inode.findTargetBlock(entry.seekPtr);

            // no target block was found
            if (blockNumber == FileSystemHelper.FREE) {

                // get a new block
                blockNumber = superblock.getFreeBlock();

                // if there was a free direct pointer for this block
                if (entry.inode.getFreeDirectPoinerForBlock(blockNumber) > FileSystemHelper.FREE) {
                    // do nothing

                    // try to get indirect pointer
                } else if (entry.inode.indirect == FileSystemHelper.FREE) {
                    blockNumber = superblock.getFreeBlock();
                    entry.inode.setIndirectPointer(blockNumber);
                } else {
                    entry.inode.setIndexBlock(blockNumber);
                }
            } else {
                SysLib.rawread(blockNumber, data); // read from currBlock
            }

            int pointer = entry.seekPtr % Disk.blockSize;
            int bytesInBlock = Disk.blockSize - pointer;

            // if there is any data left go ahead and write it
            if (bytesInBlock > bytesInBuffer) {
                System.arraycopy(buffer, writtenBytes, data, pointer, bytesInBuffer);
                SysLib.rawwrite(blockNumber, data);
                writtenBytes = writtenBytes + bytesInBuffer;
                bytesInBuffer = bytesInBuffer - bytesInBuffer;
                entry.seekPtr = entry.seekPtr + bytesInBuffer;
            } else { // write to the remainder in blocks
                System.arraycopy(buffer, writtenBytes, data, pointer, bytesInBlock);
                SysLib.rawwrite(blockNumber, data);
                writtenBytes = writtenBytes + bytesInBlock;
                bytesInBuffer = bytesInBuffer - bytesInBlock;
                entry.seekPtr = entry.seekPtr + bytesInBlock;
            }
        }

        switch (entry.mode) {
            case Mode.READ_WRITE:
                int diffInSize = fileSize(entry) - writtenBytes;
                if (diffInSize < 0) {
                    entry.inode.length = entry.inode.length + Math.abs(diffInSize);
                }
                break;

            case Mode.APPEND:
                entry.inode.length = fileSize(entry) + writtenBytes;
                break;

            default:
                entry.inode.length = entry.inode.length + writtenBytes;
                break;
        }

        entry.inode.toDisk(entry.iNumber);  // write back to disk

        return writtenBytes; // return number of bytes that have been written
    }


    //Writes the contents of buffer to the file indicated by fd, starting
    //at the position indicated by the seek pointer. The operation may
    //overwrite existing data in the file and/or append to the end of the file.
    //SysLib.write increments the seek pointer by the number of bytes to have
    //been written. The return value is the number of bytes that have been
    //written, or a negative value upon an error.

    //Updates the seek pointer corresponding to fd
    //The offset can be positive or negative.
    //The offset location of the seek pointer in the file is
    //returned from the call to seek.
    public int seek(int fd, int offset, int whence) {

        FileTableEntry temp = (FileTableEntry) filetable.table.get(fd);

        if (temp == null)
            return -1;

        int ptr = temp.seekPtr;

        //If whence is SEEK_SET (= 0), the file's seek pointer is
        //set to offset bytes from the beginning of the file
        if (whence == SEEK_SET)
            ptr = offset;

        //If whence is SEEK_CUR (= 1), the file's seek pointer is
        //set to its current value plus the offset.
        if (whence == SEEK_CUR)
            ptr = ptr + offset;

        //If whence is SEEK_END (= 2), the file's seek pointer is
        //set to the size of the file plus the offset.
        if (whence == SEEK_END)
            ptr = temp.inode.length + offset;

        //If the user attempts to set the seek pointer to a
        //negative number you must clamp it to zero.
        if (ptr < 0)
            ptr = 0;

        //If the user attempts to set the pointer to beyond the file size,
        //you must set the seek pointer to the end of the file.
        if (ptr > temp.inode.length)
            ptr = temp.inode.length;

        filetable.table.elementAt(fd).seekPtr = ptr;
        return 0;
    }

    //Closes the file corresponding to fd, commits all file
    //transactions on this file, and unregisters fd from the user
    //file descriptor table of the calling thread's TCB. The return
    //value is 0 in success, otherwise -1.

    // TODO: I don't think we should synchronize here, especially when it is on a loca
    // variable
    public int close(FileTableEntry entry) {
        if (entry == null)
            return -1;

        entry.count--;

        if (entry.count == 0) {
            boolean r = filetable.ffree(entry);
            if(r = true)
                return 0;
            else
                return -1;
        }

        return 0;
    }

    //Deletes the file specified by fileName.
    //All blocks used by file are freed. If the file is currently
    //open, it is not deleted and the operation returns a -1. If
    // successfully deleted a 0 is returned.
    public synchronized int delete(String fileName) {

        short iNumber = directory.getInumberByFileName(fileName);

        // if the file is in the directory
        if (iNumber != FileSystemHelper.FREE) {
            FileTableEntry entry = filetable.table.get(iNumber);
            deallocateBlocksForEntry(entry); // release all blocks

            //close(iNumber); // set flags, write to disk, remove from ftEnt
            directory.removeFromDirectory(iNumber);
            return 0;
        }

        return -1;

    }

    private void deallocateBlocksForEntry(FileTableEntry fileTableEntry) {
        if (fileTableEntry == null) {
            return;
        }

        if (deallocateIndirectBlocks(fileTableEntry) == false) {
            return;
        }

        this.deallocateDirectBlocks(fileTableEntry);
        fileTableEntry.inode.toDisk(fileTableEntry.iNumber);
    }

    private void deallocateDirectBlocks(FileTableEntry fileTableEntry) {
        for (int index = 0; index < FileSystemHelper.directSize; index++) {
            if (fileTableEntry.inode.direct[index] != FileSystemHelper.FREE) {
                superblock.freeBlock(fileTableEntry.inode.direct[index]);
                fileTableEntry.inode.direct[index] = FileSystemHelper.FREE;
            }
        }
    }

    private boolean deallocateIndirectBlocks(FileTableEntry fileTableEntry) {
        if (fileTableEntry.inode.indirect == FileSystemHelper.INVALID) {
            return false;
        }

        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(fileTableEntry.inode.indirect, data);
        fileTableEntry.inode.indirect = FileSystemHelper.FREE;

        int blockNumber = SysLib.bytes2short(data, 0);
        if (blockNumber != FileSystemHelper.FREE) {
            superblock.freeBlock(blockNumber);
        }
        return true;
    }


    //Returns the size in bytes of the file indicated by fd.
    public int fileSize(FileTableEntry entry) {
        if (entry == null) {
            return FileSystemHelper.INVALID;
        }

        return entry.inode.length;
    }


}