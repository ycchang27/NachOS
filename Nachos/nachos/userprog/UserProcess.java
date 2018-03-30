package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
		
		
		boolean inStatus=Machine.interrupt().disable();
		// Set Process ID
		lock.acquire();
		processID = UserKernel.numProcess ++;
		lock.release();
		// Initialize fileList and filePosList
		fileList = new OpenFile[MAX_FILES];
		filePosList = new int[MAX_FILES];
		fileDeleteList = new HashSet<String>();
		
		// Set fileList's first 2 elements with stdin and stdout (supported by console)
		fileList[STDINPUT] = UserKernel.console.openForReading();
		fileList[STDOUTPUT] = UserKernel.console.openForWriting();
		Machine.interrupt().restore(inStatus);
		parent=null;
		children=new LinkedList<UserProcess>();
		childrenExitStatus=new HashMap<Integer,Integer>();
		lock = new Lock();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
	     thread = (UThread) (new UThread(this).setName(name));
	        thread.fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}
	

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		
		int transfer = 0;
		int end = vaddr + length - 1;
		
		if(vaddr < 0 || 
				end > Machine.processor().makeAddress(numPages-1,pageSize-1)) {// ||Machine.processor().pageFromAddress(vaddr) < 0) {
			Lib.debug(dbgProcess, "Read Vritual Memory: Invalid Address");
			return 0;
		}

		// for now, just assume that virtual addresses equal physical addresses
		
		for(int i = Machine.processor().pageFromAddress(vaddr);
				i <= Machine.processor().pageFromAddress(end);
				i++) {
			if((i < 0 || i > pageTable.length) || pageTable == null)
				break;
			
			int startAddress = Machine.processor().makeAddress(i,0);
			int endAddress = Machine.processor().makeAddress(i,pageSize-1);
			int amount = 0;
			int addressOffset;
			
			if(vaddr > startAddress && end < endAddress) {
				addressOffset = vaddr - startAddress;
				amount = length;
			}
			else if(vaddr <= startAddress && end < endAddress) {
				addressOffset = 0;
				amount = end - startAddress + 1;
			}
			else if(vaddr > startAddress && end >= endAddress) {
				addressOffset = vaddr - startAddress;
				amount = endAddress - vaddr + 1;			
			}
			else {
				addressOffset = 0;
				amount = pageSize;
			}
			
			int paddr = Machine.processor().makeAddress(pageTable[i].ppn, addressOffset);
			System.arraycopy(memory, paddr, data, offset+transfer, amount);
			transfer += amount;
		}
		
		/**if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
		*/
		return transfer;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		
		int end = vaddr + length - 1;
		int transfer = 0;
		
		if(vaddr < 0 || 
				end > Machine.processor().makeAddress(numPages-1,pageSize-1)) {// ||Machine.processor().pageFromAddress(vaddr) < 0) {
			Lib.debug(dbgProcess, "Read Vritual Memory: Invalid Address");
			return 0;
		}
		
		for(int i = Machine.processor().pageFromAddress(vaddr);
				i <= Machine.processor().pageFromAddress(end);
				i++) {
			if((i < 0 || i > pageTable.length) || pageTable == null || pageTable[i].readOnly)
				break;
			
			int startAddress = Machine.processor().makeAddress(i,0);
			int endAddress = Machine.processor().makeAddress(i,pageSize-1);
			int amount = 0;
			int addressOffset;
			
			if(vaddr > startAddress && end < endAddress) {
				addressOffset = vaddr - startAddress;
				amount = length;
			}
			else if(vaddr <= startAddress && end < endAddress) {
				addressOffset = 0;
				amount = end - startAddress + 1;
			}
			else if(vaddr > startAddress && end >= endAddress) {
				addressOffset = vaddr - startAddress;
				amount = endAddress - vaddr + 1;			
			}
			else {
				addressOffset = 0;
				amount = pageSize;
			}
			
			int paddr=Machine.processor().makeAddress(pageTable[i].ppn, addressOffset);
			System.arraycopy(data, offset+transfer, memory, paddr, amount);
			transfer += amount;
		}
		/**
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		
		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
		*/
		return transfer;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;

				if(pageTable[vpn] == null)
					return false;
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < pageTable.length; ++i)
            if (pageTable[i].valid) {
                UserKernel.deletePage(pageTable[i].ppn);
                pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
            }
			numPages = 0;
    	for(int i=0;i<16;i++){
			if(fileList[i]!=null){
				fileList[i].close();
				fileList[i]=null;
			}	
		}
    	coff.close();
	}    

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Halt the Nachos machine by calling Machine.halt(). Only the root process
	 * (the first process, executed by UserKernel.run()) should be allowed to
	 * execute this syscall. Any other process should ignore the syscall and return
	 * immediately.
	 * 
	 * @return -1 if an error occurred
	 */
	private int handleHalt() {
		// Return -1 if this process isn't the root
		if(processID != ROOTPROCESS) {
			return -1;
		}
		
		// Halt the machine
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	
	/**
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file.
	 *
	 * Note that create() can only be used to create files on disk; create() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 * 
	 * @param vaddr
	 * @return the new file descriptor, or -1 if an error occurred
	 * 
	 */
	private int handleCreate(int vaddr) {
		// Extract the file name
		String fileName = readVirtualMemoryString(vaddr, MAX_STRLENGTH);
		
		// Return -1 if the file name is invalid or the list is full
		int fileDescriptor = getAvailIndex();
		if(fileName == null || fileDescriptor == -1 || fileDeleteList.contains(fileName)) {
			return -1;
		}

		// Try creating the OpenFile
		OpenFile file = UserKernel.fileSystem.open(fileName, true);
		
		// Return -1 if the file creation failed
		if(file == null) {
			return -1;
		}
		
		// Insert the file in the fileList and return its file descriptor
		fileList[fileDescriptor] = file;
		return fileDescriptor;
	}

	/**
	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 * 
	 * @param vaddr
	 * @return the new file descriptor, or -1 if an error occurred.
	 * 
	 */
	private int handleOpen(int vaddr) {
		// Extract the file name
		String fileName = readVirtualMemoryString(vaddr, MAX_STRLENGTH);

		// Return -1 if the file name is invalid or the list is full
		int fileDescriptor = getAvailIndex();
		if(fileName == null || fileDescriptor == -1 || fileDeleteList.contains(fileName)) {
			return -1;
		}

		// Try creating the OpenFile
		OpenFile file = UserKernel.fileSystem.open(fileName, false);

		// Return -1 if the file creation failed
		if(file == null) {
			return -1;
		}

		// Insert the file in the fileList and return its file descriptor
		fileList[fileDescriptor] = file;
		return fileDescriptor;
	}
	
	/**
	 * Attempt to read up to size bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 * 
	 * @param fileDescriptor
	 * @param vaddr
	 * @param size
	 * @return On success, the number of bytes read is returned. On error, -1 is 
	 * returned, and the new file position is undefined.
	 * 
	 */
	private int handleRead(int fileDescriptor, int vaddr, int size) {
		// Return -1 if the input is invalid
		if(size < 0 || (fileDescriptor >= MAX_FILES || fileDescriptor < 0)
				|| fileList[fileDescriptor] == null) {
			return -1;
		}
		
		// Read up to size bytes and save the number of bytes read
		byte[] readBuffer = new byte[size];
		int bytesRead;
		if(fileDescriptor < 2) { // comment if error
			bytesRead = fileList[fileDescriptor].read(readBuffer, 0, size); 
		} // comment if error
		else {	// comment if error
			bytesRead = fileList[fileDescriptor].read(filePosList[fileDescriptor], readBuffer, 0, size); // comment if error
		}	// comment if error
		
		// Return -1 if failed to read
		if(bytesRead == -1 || bytesRead == 0) {
			return -1;
		}
		
		// Write the buffer into the virtual memory, update file position, and return bytes transferred
		int bytesTransferred = writeVirtualMemory(vaddr, readBuffer, 0, bytesRead);
		if(fileDescriptor >= 2) { // comment if error
			filePosList[fileDescriptor] += bytesTransferred;	// comment if error
		}	// comment if error
		return bytesTransferred;
	}
	
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 * 
	 * @param fileDescriptor
	 * @param vaddr
	 * @param size
	 * @return On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. On error, -1 is 
	 * returned, and the new file position is undefined.
	 * 
	 */
	private int handleWrite(int fileDescriptor, int vaddr, int size) {
		// Return -1 if the input is invalid
		if(size < 0 || (fileDescriptor >= MAX_FILES || fileDescriptor < 0)
				|| fileList[fileDescriptor] == null) {
			return -1;
		}
		
		// Count number of buffers to write
		byte[] writeBuffer = new byte[size];
		int bytesToWrite = readVirtualMemory(vaddr, writeBuffer, 0, size);
		
		// Write the file, update file position, and return number of bytes written
		int bytesWritten;
		if(fileDescriptor < 2) { // comment if error
			bytesWritten =  fileList[fileDescriptor].write(writeBuffer, 0, bytesToWrite);
		}	// comment if error
		else {	// comment if error
			bytesWritten =  fileList[fileDescriptor].write(filePosList[fileDescriptor], writeBuffer, 0, bytesToWrite);	// comment if error
		}	// comment if error
		if(fileDescriptor >= 2) {	// comment if error
			filePosList[fileDescriptor] += (bytesWritten > 0) ? bytesWritten : 0;	// comment if error
		}	// comment if error
		return (bytesWritten < size && bytesWritten != 0) ? -1 : bytesWritten;	// comment if error
		// return bytesWritten;	// uncomment if error
	}
	
	/**
	 * Close a file descriptor, so that it no longer refers to any file or stream
	 * and may be reused.
	 *
	 * If the file descriptor refers to a file, all data written to it by write()
	 * will be flushed to disk before close() returns.
	 * If the file descriptor refers to a stream, all data written to it by write()
	 * will eventually be flushed (unless the stream is terminated remotely), but
	 * not necessarily before close() returns.
	 *
	 * The resources associated with the file descriptor are released. If the
	 * descriptor is the last reference to a disk file which has been removed using
	 * unlink, the file is deleted (this detail is handled by the file system
	 * implementation).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 * 
	 * @param fileDescriptor
	 * @return 0 on success, or -1 if an error occurred.
	 * 
	 */
	private int handleClose(int fileDescriptor) {
		// Return -1 if the input is invalid
		if((fileDescriptor >= MAX_FILES || fileDescriptor < 0)
				|| fileList[fileDescriptor] == null) {
			return -1;
		}
		
		// Close and remove the element from the list
		String fileName = fileList[fileDescriptor].getName();
		fileList[fileDescriptor].close();
		fileList[fileDescriptor] = null;
		filePosList[fileDescriptor] = 0;
		
		// Attempt to delete file if this file is unlinked
		if(fileDeleteList.contains(fileName)) {	// comment if error
			if(UserKernel.fileSystem.remove(fileName) == true) {	// comment if error
				fileDeleteList.remove(fileName);	// comment if error
				return 0;	// comment if error
			}	// comment if error
			else {
				return -1;	// comment if error
			}	// comment if error
		}	// comment if error
		
		return 0;	// success
	}
	
	/**
	 * Delete a file from the file system. If no processes have the file open, the
	 * file is deleted immediately and the space it was using is made available for
	 * reuse.
	 *
	 * If any processes still have the file open, the file will remain in existence
	 * until the last file descriptor referring to it is closed. However, creat()
	 * and open() will not be able to return new file descriptors for the file
	 * until it is deleted.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 * 
	 * @param vaddr
	 * @return 0 on success, or -1 if an error occurred.
	 * 
	 */
	private int handleUnlink(int vaddr) {
		// Extract the file name
		String fileName = readVirtualMemoryString(vaddr, MAX_STRLENGTH);

		// Return -1 if the file name is invalid
		if(fileName == null) {
			return -1;
		}
		
		// Search for index
//		int fileDescriptor = searchFile(fileName);	// uncomment if error
		
//		// Return -1 if the file still exists in fileList	// uncomment if error
//		if(fileDescriptor != -1) {	// uncomment if error
//			return -1;	// uncomment if error
//		}	// uncomment if error
		
		// Attempt to remove the file from the UserKernel's fileSystem
		boolean removeSuccess = UserKernel.fileSystem.remove(fileName);

		// Just unlink if the file is being used by other processes
		if(removeSuccess == false) {	// comment if error
			fileDeleteList.add(fileName);	// comment if error
			return -1;	// comment if error
		}	// comment if error
		
		return 0;	// success	// comment if error
		// return (removeSuccess == true) ? 0 : -1;	// uncomment if error
	}

	private int handleExec(int vaddr, int a1, int a2 )
	{
		if(vaddr<0||a1<0||a2<0){
			Lib.debug(dbgProcess, "handleExec:Invalid parameter");
			return -1;
		}
		String fileName=readVirtualMemoryString(vaddr, 256);
		if(fileName==null){
			Lib.debug(dbgProcess, "handleExec:Read filename failed");
			return -1;
		}
		if(!fileName.contains(".coff")){
			Lib.debug(dbgProcess, "handleExec:Filename should end with .coff");
			return -1;
		}
		String[] args=new String[a1];
		for(int i=0;i<a1;i++){
			byte[] buffer=new byte[4];
			int readLength;
			readLength=readVirtualMemory(a2+i*4,buffer);
			if(readLength!=4){
				Lib.debug(dbgProcess, "handleExec:Read argument address falied");
				return -1;
			}
			int argVAddr=Lib.bytesToInt(buffer, 0);
			String arg=readVirtualMemoryString(argVAddr,256);
			if(arg==null){
				Lib.debug(dbgProcess, "handleExec:Read argument failed");
				return -1;
			}
			args[i]=arg;
		}
		UserProcess child=UserProcess.newUserProcess();
		boolean isSuccessful=child.execute(fileName, args);
		if(!isSuccessful){
			Lib.debug(dbgProcess, "handleExec:Execute child process failed");
			return -1;
		}
		
		child.parent=this;
		this.children.add(child);
		int id=child.processID;
		return id;

	}
	
	private int handleExit(int status)
	{
		
		if(parent!=null){
			parent.lock.acquire();
			parent.childrenExitStatus.put(processID, status);
			parent.lock.release();
			//parent.children.remove(this);
		}
		unloadSections();
		int childrenNum=children.size();
		for(int i=0;i<childrenNum;i++){
			UserProcess child=children.removeFirst();
			child.parent=null;
		}
		System.out.println("hehe exit"+processID+status);

		if(processID==0){
			Kernel.kernel.terminate();
		}else{
			UThread.finish();
		}
		return 0;

}
	
	private int handleJoin(int pID,int statusVAddr){
		if(pID<0||statusVAddr<0){
			return -1;
		}
		
		UserProcess child=null;
		int childrenNum=children.size();
		for(int i=0;i<childrenNum;i++){
			if(children.get(i).processID==pID){
				child=children.get(i);
			
				break;
			}
		}
		
		if(child==null){
			Lib.debug(dbgProcess, "handleJoin:pID is not the child");
			return -1;
		}
		//System.out.println("debug information"+child.pID);
		child.thread.join();

		child.parent=null;
		lock.acquire();
		Integer status=childrenExitStatus.get(child.processID);
		lock.release();
		
		children.remove(child);
		if(status == null){
			Lib.debug(dbgProcess, "handleJoin:Cannot find the exit status of the child");
			return 0;
		}else{
			//status int 32bits
			byte[] buffer=new byte[4];
			buffer=Lib.bytesFromInt(status);
			int count=writeVirtualMemory(statusVAddr,buffer);
			if(count==4){
				return 1;
			}else{
				Lib.debug(dbgProcess, "handleJoin:Write status failed");
				return 0;
			}
		}
	}

	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 * 
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallExit:
			return handleExit(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3)
					);
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;				       

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	
	/** Array of files that UserProcess manipulates */
	private OpenFile[] fileList;
	private final int MAX_FILES = 16;
	private final int MAX_STRLENGTH = 256;
	private int[] filePosList;	// corresponding files
	
	/** HashSet of whether the file is to be deleted, not allowing creat or open */
	private static HashSet<String> fileDeleteList;
	
	/** Get the next available index for fileList */
	private int getAvailIndex() {
		for(int i = 2; i < MAX_FILES; i++) {
			if(fileList[i] == null) {
				return i;
			}
		}
		return -1;
	}
	
	/** file descriptor search function for fileList */
	private int searchFile(String fileName) {
		for(int fileDescriptor = 0; fileDescriptor < MAX_FILES; fileDescriptor++) {
			if(fileList[fileDescriptor] != null 
					&& fileList[fileDescriptor].getName().equals(fileName)) {
				return fileDescriptor;
			}
		}
		return -1;
	}
	
	/** "enum" */
	private final int STDINPUT = 0;
	private final int STDOUTPUT = 1;
	private final int ROOTPROCESS = 0;
	
	/** Process ID */
	private int processID;
	
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	protected UserProcess parent;
	protected LinkedList<UserProcess> children;
	private static Lock lock = new Lock();
	protected UThread thread;
	protected HashMap<Integer,Integer> childrenExitStatus;
}