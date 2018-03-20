package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;

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

		// initialize the array of processes (OpenFiles)
		processList = new Process[MAX_PROCESSES];

		// index 0 = console's reader, index 1 = console's writer
		processList[0] = new Process(UserKernel.console.openForReading());
		processList[1] = new Process(UserKernel.console.openForWriting());
		
		// set up next avail index for processList
		numProcesses = 2;	// because 0 and 1 are taken
		
		// set up number of Processes to fill
		numProcessesToFill = 0;	// no "holes" in the array yet
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

		new UThread(this).setName(name).fork();

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

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
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

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int virtualPageNumber = Machine.processor().pageFromAddress(vaddr);
		int offsetForAddress = Machine.processor().offsetFromAddress(vaddr);
		int numPages = Machine.processor().getNumPhysPages();
		
		
		TranslationEntry e = pageTable[virtualPageNumber];
		e.dirty = true;
		e.used = true;
		
		int physicalPageNum = e.ppn;
		int physicalAddress = (physicalPageNum * pageSize) + offsetForAddress;
		
		if (e.readOnly == true || // if 
				physicalPageNum < 0 || // invalid page number 
				physicalPageNum >= numPages) // if physical page number amount exceeds what there is
		{
			
			return 0;
		}
		
		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
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
		int machineNumOfPages = Machine.processor().getNumPhysPages();
		if (numPages > machineNumOfPages) {
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

				// for now, just assume virtual addresses=physical addresses
				
				// accessing pageTable with virtual page number and saving that value into entry variable 
				TranslationEntry entry = pageTable[vpn];
				
				// setting boolean value 
				entry.readOnly = section.isReadOnly();
				
				section.loadPage(i, entry.ppn);
			}
			
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		int i = 0;
		while (i < numPages) {
			UserKernel.addAPage(pageTable[i].ppn);
			pageTable[i].valid = false;
			i++;
		}
		
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
	 * Handle the halt() system call. 
	 */
	private int handleHalt() {
		// implement me
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the create system call.
	 */
	private int handleCreate(int vaddr) {
		// find the file name and search for the file in the file system
		String fileName = readVirtualMemoryString(vaddr, MAX_NUM_VIRTUAL_ARR_LENGTH);
		
		// check if the file name is valid
		if(fileName == null) {
			return -1;
		}
		
		// check if the file exists
		OpenFile file = UserKernel.fileSystem.open(fileName, false);
		if(file != null) {
			// do nothing
			return -1;
		}

		// create the file and return its index (fileDescriptor)
		int fileDescriptor = getNextEmptyProcess();
		
		// check if there is a spot
		if(fileDescriptor != -1) {
			// there is a spot to insert Process
			file = new OpenFile(UserKernel.fileSystem, fileName);
			processList[fileDescriptor] = new Process(file);
			numProcesses ++;
		}
		
		// return error or the new Process's index
		return fileDescriptor;
	}

	/**
	 * Handle the open system call.
	 */
	private int handleOpen(int vaddr) {
		// find the file name and search for the file in the file system
		String fileName = readVirtualMemoryString(vaddr, MAX_NUM_VIRTUAL_ARR_LENGTH);

		// check if the file name is valid
		if(fileName == null) {
			return -1;
		}

		// check if the file exists
		OpenFile file = UserKernel.fileSystem.open(fileName, false);
		if(file == null) {
			// do nothing
			return -1;
		}

		// find the index of the OpenFile in the processList
		// start at 2 because 0 and 1 are taken (console input and output)
		int fileDescriptor = searchProcess(fileName);
		
		// unexpected error check
		if(fileDescriptor == -1) {
			// this should not be called (unexpected error)
			Lib.assertNotReached("handleOpen(int vaddr) could not find the OpenFile in processList!");
		}
		
		return fileDescriptor;
	}
	
	/**
	 * Handle the read system call.
	 */
	private int handleRead(int fileDescriptor, int vaddr, int size) {
		// checks validity of the fileDescriptor
		if((fileDescriptor < 0 || fileDescriptor >= MAX_PROCESSES)
				|| processList[fileDescriptor] == null) {
			return -1;
		}
		
		// read the buffer
		byte[] buffer = new byte[size];
		Process process = processList[fileDescriptor];
		int newPosition = process.file.read(process.filePosition, buffer, 0, size);
		
		// check whether the read was successful
		if(newPosition >= 0) {
			int positionOffset = writeVirtualMemory(vaddr, buffer);
			processList[fileDescriptor].filePosition += positionOffset;
		}
			
		// return error or the position offset
		return newPosition;
	}
	
	/**
	 * Handle the write system call.
	 */
	private int handleWrite(int fileDescriptor, int vaddr, int size) {
		// checks validity of the fileDescriptor
		if((fileDescriptor < 0 || fileDescriptor >= MAX_PROCESSES)
				|| processList[fileDescriptor] == null) {
			return -1;
		}
		
		// read the buffer
		byte[] buffer = new byte[size];
		int byteCount = readVirtualMemory(vaddr, buffer);
		
		// write the buffer
		Process process = processList[fileDescriptor];
		int positionOffset = process.file.write(process.filePosition, buffer, 0, byteCount);
		
		// check whether the read was successful
		if(positionOffset >= 0) {
			processList[fileDescriptor].filePosition += positionOffset;
		}
		
		// return error or the position offset
		return positionOffset;
	}
	
	/**
	 * Handle the close system call.
	 */
	private int handleClose(int fileDescriptor) {
		// checks validity of the fileDescriptor
		if((fileDescriptor < 0 || fileDescriptor >= MAX_PROCESSES)
				|| processList[fileDescriptor] == null) {
			return -1;
		}
		
		// close the process
		processList[fileDescriptor].file.close();
		
		// remove the file from the file system if it's supposed to be removed
		if(processList[fileDescriptor].removeMe == true) {
			UserKernel.fileSystem.remove(processList[fileDescriptor].file.getName());
		}
		
		// empty the spot
		processList[fileDescriptor] = null;
		
		// close successful
		return 0;
	}
	
	/**
	 * Handle the unlink system call.
	 */
	private int handleUnlink(int fileDescriptor) {
		// checks validity of the fileDescriptor
		if(fileDescriptor < 0 || fileDescriptor >= MAX_PROCESSES) {
			return -1;
		}
		
		// if the file doesn't exist in processList, then remove it in the file system
		if(processList[fileDescriptor] == null) {
			boolean success = UserKernel.fileSystem.remove(processList[fileDescriptor].file.getName());
			return (success == true) ? 0 : -1;
		}
		
		// "unlink" by setting the boolean to true, close syscall will remove it later
		processList[fileDescriptor].removeMe = true;
		
		// unlink successful
		return 0;
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

	/** Max length for virtual memory */
	private static final int MAX_NUM_VIRTUAL_ARR_LENGTH = 256;

	/** Array of "processes", which can be traversed by an index called "file descriptor" */
	private Process[] processList;

	/**
	 * A class that represents a process. Its variables are declared as public
	 * to make it easier to access in UserProcess class. Do not abuse!
	 */
	private class Process {
		public OpenFile file;		// corresponding file
		public int filePosition;	// corresponding file position
		public boolean removeMe;	// determines whether this Process should be removed

		// constructor
		public Process(OpenFile file) {
			this.file = file;
			filePosition = 0;
			removeMe = false;
		}
	}
	
	/**
	 * Finds the next available index to insert for processList. If the list is full, return -1.
	 */
	private int getNextEmptyProcess() {
		// return -1 if the array is full
		if(numProcesses == MAX_PROCESSES) {
			return -1;
		}
		// return current number of processes if there are no "holes" to fill
		else if(numProcessesToFill == 0) {
			return numProcesses;
		}
		// find the next empty spot
		for(int fileDescriptor = 2; fileDescriptor < numProcesses; fileDescriptor ++) {
			// if found, return the index (fileDescriptor)
			if(processList[fileDescriptor] == null) {
				return fileDescriptor;
			}
		}
		
		// unexpected error
		Lib.assertNotReached("getNextEmptyProcess() got an unexpected issue!");
		return -1;
	}
	
	/**
	 * Finds the Process's index that corresponds with file name. Returns -1, if not found.
	 */
	private int searchProcess(String fileName) {
		// search in the array
		for(int fileDescriptor = 2; fileDescriptor < numProcesses; fileDescriptor ++) {
			// if found, return the index (fileDescriptor)
			if(processList[fileDescriptor].file.getName().equals(fileName)) {
				return fileDescriptor;
			}
		}
		// search failed
		return -1;
	}
	
	/** processList variables*/
	private static final int MAX_PROCESSES = 16;
	private int numProcesses;
	private int numProcessesToFill;	// # of processes to fill the "holes" in the list like [x,null,y,z]

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
		// case syscallExit:
		// 	return handleExit(a0);
		// case syscallExec:
		// 	return handleExec(a0, a1, a2);
		// case syscallJoin:
		// 	return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
		 	return handleRead(a0, a1, a2);
		case syscallWrite:
		 	return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
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
}
