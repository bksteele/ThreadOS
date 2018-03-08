/*Edited FileTable class for CSS430 Final Project


 */
import java.util.Vector;

public class FileTable {

    public Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory


    public FileTable( Directory directory ) { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    // major public methods
    // allocate a new file (structure) table entry for this file name
    public synchronized FileTableEntry falloc( String filename, String mode ) {
        Inode node = null;
        //nodeNum has to be a short because of Inode
        short nodeNum = -1;

        //check for "/" or file name
        if(filename.equals("/"))
            nodeNum = 0;
        else
            nodeNum = dir.namei(filename);

        while(true)
        {
            //check for "/" or file name
            if(filename.equals("/"))
                nodeNum = 0;
            else
                nodeNum = dir.namei(filename);

            //Check for file
            if(nodeNum >= 0)
            {
                node = new Inode(nodeNum);
                if(mode.equals("r"))
                {
                    if(node.flag == 3)
                    {
                        try{
                            wait();
                        } catch(InterruptedException e) {}
                        break;
                    }
                    else if(node.flag == 4)
                    {
                        nodeNum = -1;
                        return null;
                    }
                    else{
                        node.flag = 2;
                        break;
                    }
                }

                else
                {
                    //if the node hasn't been edited at all, it's write(3)
                    if(node.flag == 0 || node.flag == 1)
                        node.flag = 3;

                    //if the node is busy, wait
                    else if(node.flag == 2 || node.flag == 3)
                    {
                        try{
                            wait();
                        } catch (InterruptedException e) {}
                        break;
                    }

                    else if(node.flag == 4)
                    {
                        nodeNum = -1;
                        return null;
                    }
                }
            }
            else
            {
                nodeNum = dir.ialloc(filename);
                node = new Inode();
                break;
            }
        }
        // allocate/retrieve and register the corresponding inode using dir
        // increment this inode's count
        node.count++;
        // immediately write back this inode to the disk
        node.toDisk(nodeNum);
        // return a reference to this file (structure) table entry
        FileTableEntry retVal = new FileTableEntry(node, nodeNum, mode);
        table.addElement(retVal);
        return retVal;
    }

    public synchronized boolean ffree( FileTableEntry e ) {
        // receive a file table entry reference
        Inode node = new Inode(e.iNumber);

        // return true if this file table entry found in my table
        if(table.Contains(e)) {

            e.inode.count--;
            e.inode.flag = 0;

            // save the corresponding inode to the disk
            node.toDisk(e.iNumber);
            // free this file table entry.
            table.remove(e);
            return true;
        }
        return false;
    }

    public synchronized boolean fempty( ) {
        return table.isEmpty( );  // return if table is empty
    }                            // should be called before starting a format
}