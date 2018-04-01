package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */

	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];

		for (int i = 0; i < numPhysPages; i++) {
			pageTable[i] = new TranslationEntry(i, 0, false, false, false, false);
		}

		//create a new lock for the function implementation
				lock = new Lock();
				
				boolean stat =Machine.interrupt().disable();
				// Set Process ID
				lock.acquire();
				processID = counter ++;
				lock.release();
				
				
				// Initialize fileList
				fileList = new OpenFile[MAX_FILES];
				
				// Set fileList's first 2 elements with stdin and stdout (supported by console)
				fileList[STDINPUT] = UserKernel.console.openForReading();
				fileList[STDOUTPUT] = UserKernel.console.openForWriting();
				
				Machine.interrupt().restore(stat);
				
				//make the parent process null for a given user process
				parentProcess=null;
				
				//make a list of child processes for a "parent" to have if it does have children
				childProcesses=new LinkedList<UserProcess>();
				
				//maintain the list of children status regarding whether to exit or not
				childProcessStatus=new HashMap<Integer,Integer>();
				
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
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

	/*
	* Allocates 
	*
	*
	*/
	protected boolean allocate(int vpn, int desiredPages, boolean readOnly) {

		LinkedList<TranslationEntry> allocated = new LinkedList<TranslationEntry>();
		
		for (int i = 0; i < desiredPages; ++i) {
			if (vpn >= pageTable.length)
				return false;
			int ppn = UserKernel.getPage();

			if (ppn != -1) {
				TranslationEntry a = new TranslationEntry(vpn + i, ppn, true, readOnly, false, false);
				allocated.add(a);
				pageTable[vpn + i] = a;
				++numPages;
			} else {
				for (TranslationEntry te : allocated) {
					pageTable[te.vpn] = new TranslationEntry(te.vpn, 0, false, false, false, false);
					UserKernel.deletePage(te.ppn);
					--numPages;
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		if(numPages == 0) {
			Lib.debug(dbgProcess, "Read Vritual Memory: Empty pageTable");
			return 0;
		}

		byte[] memory = Machine.processor().getMemory();

		int transfer = 0;
		int end = vaddr + length - 1;

		if (vaddr < 0 || end > Machine.processor().makeAddress(numPages - 1, pageSize - 1)) {// ||Machine.processor().pageFromAddress(vaddr) < 0) {
			Lib.debug(dbgProcess, "Read Vritual Memory: Invalid Address");
			return 0;
		}

		// for now, just assume that virtual addresses equal physical addresses

		for (int i = Machine.processor().pageFromAddress(vaddr); i <= Machine.processor().pageFromAddress(end); i++) {
			if ((i < 0 || i > pageTable.length) || pageTable == null || !pageTable[i].valid)
				break;

			int startAddress = Machine.processor().makeAddress(i, 0);
			int endAddress = Machine.processor().makeAddress(i, pageSize - 1);
			int amount = 0;
			int addressOffset;

			if (vaddr > startAddress && end < endAddress) {
				addressOffset = vaddr - startAddress;
				amount = length;
			} else if (vaddr <= startAddress && end < endAddress) {
				addressOffset = 0;
				amount = end - startAddress + 1;
			} else if (vaddr > startAddress && end >= endAddress) {
				addressOffset = vaddr - startAddress;
				amount = endAddress - vaddr + 1;
			} else {
				addressOffset = 0;
				amount = pageSize;
			}

			int paddr = Machine.processor().makeAddress(pageTable[i].ppn, addressOffset);
			System.arraycopy(memory, paddr, data, offset + transfer, amount);
			transfer += amount;
		}

		return transfer;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		
		if(numPages >= pageTable.length) {
			Lib.debug(dbgProcess, "Write Virtual Memoy: pageTable full");
			return 0;
		}

		byte[] memory = Machine.processor().getMemory();

		int end = vaddr + length - 1;
		int transfer = 0;

		if (vaddr < 0 || end > Machine.processor().makeAddress(numPages - 1, pageSize - 1)) {// ||Machine.processor().pageFromAddress(vaddr) < 0) {
			Lib.debug(dbgProcess, "Write Virtual Memory: Invalid Address");
			return 0;
		}

		for (int i = Machine.processor().pageFromAddress(vaddr); i <= Machine.processor().pageFromAddress(end); i++) {
			if ((i < 0 || i > pageTable.length) || pageTable == null || pageTable[i].readOnly || !pageTable[i].valid)
				break;

			int startAddress = Machine.processor().makeAddress(i, 0);
			int endAddress = Machine.processor().makeAddress(i, pageSize - 1);
			int amount = 0;
			int addressOffset;

			if (vaddr > startAddress && end < endAddress) {
				addressOffset = vaddr - startAddress;
				amount = length;
			} else if (vaddr <= startAddress && end < endAddress) {
				addressOffset = 0;
				amount = end - startAddress + 1;
			} else if (vaddr > startAddress && end >= endAddress) {
				addressOffset = vaddr - startAddress;
				amount = endAddress - vaddr + 1;
			} else {
				addressOffset = 0;
				amount = pageSize;
			}

			int paddr = Machine.processor().makeAddress(pageTable[i].ppn, addressOffset);
			System.arraycopy(data, offset + transfer, memory, paddr, amount);
			transfer += amount;
		}
		return transfer;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	protected boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			if (!allocate(numPages, section.getLength(), section.isReadOnly())) {
				for (int i = 0; i < pageTable.length; ++i)
					if (pageTable[i].valid) {
						UserKernel.deletePage(pageTable[i].ppn);
						pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
					}
				numPages = 0;
				return false;
			}
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
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
		if (!allocate(numPages, stackPages, false)) {
			for (int i = 0; i < pageTable.length; ++i)
				if (pageTable[i].valid) {
					UserKernel.deletePage(pageTable[i].ppn);
					pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
				}
			numPages = 0;
			return false;
		}
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		if (!allocate(numPages, 1, false)) {
			for (int i = 0; i < pageTable.length; ++i)
				if (pageTable[i].valid) {
					UserKernel.deletePage(pageTable[i].ppn);
					pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
				}
			numPages = 0;
			return false;
		}

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				TranslationEntry te = pageTable[vpn];
				if (te == null)
					return false;
				section.loadPage(i, te.ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		int i;
		for (i = 0; i < pageTable.length; ++i)
			if (pageTable[i].valid) {
				UserKernel.deletePage(pageTable[i].ppn);
				pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
			}
		numPages = 0;
		for (i = 0; i < 16; i++) {
			if (fileList[i] != null) {
				fileList[i].close();
				fileList[i] = null;
			}
		}
		coff.close();

	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
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
		if (processID != 0) {
			System.out.println("processID is not root");
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExit(int status)
	{
		//check to see if the parent process is valid
		if(parentProcess != null)
		{
			//acquire the lock for the parent process
			lock.acquire();
			//add the process id for process into the children processes status list
			parentProcess.childProcessStatus.put(processID, status);
			//release the lock
			lock.release();
		}
		//unload all the information
		unloadSections();
		
		//hold the size of the children list
		int numberOfChildren = childProcesses.size();
		
		//iterate through children list
		for(int i = 0; i < numberOfChildren; i++)
		{
			//remove all the children from the childProcessList
			UserProcess child = childProcesses.removeFirst();
			//set the child parent back to null
			child.parentProcess = null;
		}
		
		//if the process id is the root terminate 
		if(processID == 0)
			Kernel.kernel.terminate();
		
		//finish the thread and then return 0
		else
			UThread.finish();
		return 0;

	}
	

	private int handleJoin(int processID,int virtualAddress)
	{
		//if process ID or virtual Address is out of range
		if(processID < 0 || virtualAddress < 0)
		{
			return -1;
		}
		
		//create a child process variable
		UserProcess child = null;
		
		//get the current number of child processes
		int numberOfChildren = childProcesses.size();
		
		
		//iterate through each child
		for(int i = 0; i < numberOfChildren; i++)
		{
			//if child has the same process ID, its okay to join
			if(childProcesses.get(i).processID==processID)
			{
				//set the child as the child in the list with the matching ID
				child = childProcesses.get(i);
			
				break;
			}
		}
		
		//if the result is null, no parent can join to the child
		if(child == null)
		{
			Lib.debug(dbgProcess, "handleJoin: child cannot allow join");
			return -1;
			
		}
		
		//at this point the child should be able to join
		//using the join function, join the child thread
		child.thread.join();

		//set the child's parent to null
		child.parentProcess = null;
		
		//critical code
		lock.acquire();
		//wait for the lock and get the child process ID
		Integer statusCheck = childProcessStatus.get(child.processID);
		lock.release();
		
		//remove the child from the childProcesses Join List
		childProcesses.remove(child);
		
		//if the status is null, child cannot exit
		if(statusCheck == null)
			return 0;
		
		//if status is not null, the child can exit
		else
		{
			//create a new buffer of 4 bytes
			byte[] statusBuffer=new byte[4];
			
			//get the buffer
			statusBuffer=Lib.bytesFromInt(statusCheck);
			

			//if the value status is 4 bytes we can return 1
			if(writeVirtualMemory(virtualAddress,statusBuffer) == 4)
			{
				return 1;
			}
			else
			{
				Lib.debug(dbgProcess, "handleJoin: cannot write");
				
				return 0;
			}
		}
	}

	private int handleCreate(int vaddr) {
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "handleCreate:Invalid virtual address");
			return -1;
		}
		String fileName = readVirtualMemoryString(vaddr, 256);
		if (fileName == null) {
			Lib.debug(dbgProcess, "handleCreate:Read filename failed");
			return -1;
		}
		int availableIndex = -1;
		for (int i = 0; i < 16; i++) {
			if (fileList[i] == null) {
				availableIndex = i;
				break;
			}
		}
		if (availableIndex == -1) {
			Lib.debug(dbgProcess, "handleCreate:Cannot create more than 16 files");
			return -1;
		} else {
			OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
			if (file == null) {
				Lib.debug(dbgProcess, "handleCreate:Create failed");
				return -1;
			} else {
				fileList[availableIndex] = file;
				return availableIndex;
			}
		}

	}

	private int handleOpen(int vaddr) {
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "handleOpen:Invalid virtual address");
			return -1;
		}
		String fileName = readVirtualMemoryString(vaddr, 256);
		if (fileName == null) {
			Lib.debug(dbgProcess, "handleOpen:Read filename failed");
			return -1;

		}
		int availableIndex = -1;
		for (int i = 0; i < 16; i++) {
			if (fileList[i] == null) {
				availableIndex = i;
				break;
			}
		}
		if (availableIndex == -1) {
			Lib.debug(dbgProcess, "handleOpen:Cannot create more than 16 files");
			return -1;
		} else {
			OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
			if (file == null) {
				Lib.debug(dbgProcess, "handleOpen:Open failed");
				return -1;
			} else {
				fileList[availableIndex] = file;
				return availableIndex;
			}
		}
	}

	private int handleRead(int descriptor, int bufferVAddr, int size) {
		if (descriptor < 0 || descriptor > 15 || size < 0) {
			return -1;
		}
		OpenFile file;

		if (fileList[descriptor] == null) {
			Lib.debug(dbgProcess, "handleRead:File doesn't exist in the descriptor table");
			return -1;
		} else {
			file = fileList[descriptor];
		}
		int length = 0;
		byte[] reader = new byte[size];
		length = file.read(reader, 0, size);
		if (length == -1) {
			Lib.debug(dbgProcess, "handleRead:Error occurred when try to read file");
			return -1;
		}
		int count = 0;
		count = writeVirtualMemory(bufferVAddr, reader, 0, length);
		return count;

	}

	private int handleWrite(int descriptor, int bufferVAddr, int size) {
		if (descriptor < 0 || descriptor > 15) {
			Lib.debug(dbgProcess, "hanleWirte:Descriptor out of range");
			return -1;
		}
		if (size < 0) {
			Lib.debug(dbgProcess, "handleWrite:Size to write cannot be negative");
			return -1;
		}
		OpenFile file;
		if (fileList[descriptor] == null) {
			Lib.debug(dbgProcess, "handleWrite:File doesn't exist in the descriptor table");
			return -1;
		} else {
			file = fileList[descriptor];
		}
		int length = 0;
		byte[] writer = new byte[size];
		length = readVirtualMemory(bufferVAddr, writer, 0, size);
		int count = 0;
		count = file.write(writer, 0, length);
		//System.out.println(size==count);
		if (count == -1) {
			Lib.debug(dbgProcess, "handleWrite:Error occur when read file");
			return -1;
		}
		return count;
	}

	private int handleClose(int descriptor) {
		if (descriptor < 0 || descriptor > 15) {
			Lib.debug(dbgProcess, "handleClose:Descriptor out of range");
			return -1;
		}
		if (fileList[descriptor] == null) {
			Lib.debug(dbgProcess, "handleClose:File doesn't exist in the descriptor table");
			return -1;
		} else {
			fileList[descriptor].close();
			fileList[descriptor] = null;
		}
		return 0;
	}

	private int handleUnlink(int vaddr) {
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "handleUnlink:Invalid virtual address");
			return -1;
		}
		String fileName = readVirtualMemoryString(vaddr, 256);

		if (fileName == null) {
			Lib.debug(dbgProcess, "handleUnlink:Read filename failed");
			return -1;
		}
		OpenFile file;

		int index = -1;

		int j = 0;
		while (j < 16) {
			file = fileList[j];
			if (file != null && file.getName().compareTo(fileName) == 0) {
				index = j;
				break;
			}
			j++;
		}

		if (index != -1) {
			System.out.println("handleUnlink: File should be closed first");
			return -1;
		}

		boolean didRemove = ThreadedKernel.fileSystem.remove(fileName);

		if (!didRemove) {
			System.out.println("handleUnlink:Remove failed");
			return -1;
		}

		return 0;

	}
	private int handleExec(int virtualAddress, int arg1, int arg2 )
	{
		
		//check to see if arguments are valid
		if(virtualAddress < 0 || arg1 < 0 || arg2 < 0)
		{
			Lib.debug(dbgProcess, "handleExec:Invalid entry");
			return -1;
		}
		
		//grab the filename from the virtual address
		//check to see if the address exists
		String fileName = readVirtualMemoryString(virtualAddress, 256);
		
		//if file name does not exist return no process ID
		if(fileName == null)
		{
			Lib.debug(dbgProcess, "handleExec:file name does not exist");
			return -1;
		}
		
		
		//check that the file extension is the correct format
		if(fileName.contains(".coff") == false)
		{
			Lib.debug(dbgProcess, "handleExec: Incorrect file format, need to end with .coff");
			return -1;
		}
		
		//make an array to hold the given arguments
		String[] argsHolder = new String[arg1];
		
		//iterate through all values less than argument 1's length
		for(int i=0;i<arg1;i++)
		{
			//create a new buffer
			byte[] argBuffer = new byte[4];
			//create a new variable to hold the length of the virtual memory read
			int memReadLen = readVirtualMemory(arg2 + i*4, argBuffer);
			
			//if address not 4 bytes return -1
			if(memReadLen != 4)
			{
				Lib.debug(dbgProcess, "handleExec:argument address incorect size");
				return -1;
			}
			
			//create a new variable to hold the argument virtual address
			int argVirtualAddress = Lib.bytesToInt(argBuffer, 0);
			
			//get the virtual memory string from the virtual address previously grabbed 
			String arg = readVirtualMemoryString(argVirtualAddress,256);
			
			
			if(arg==null)
			{
				Lib.debug(dbgProcess, "handleExec:arugment null");
				return -1;
			}
			
			//store the argument into the args holder object
			argsHolder[i]=arg;
		}
		
		//create a new child process 
		UserProcess childPro=UserProcess.newUserProcess();
		
		//if the child process cannot execute, return -1
		if(childPro.execute(fileName, argsHolder) == false)
		{
			Lib.debug(dbgProcess, "handleExec:child process failure");
			return -1;
		}
		
		
		//set the child process's parent to this process
		childPro.parentProcess = this;
		
		//add the child to the child processes list
		this.childProcesses.add(childPro);
		
		//return the child process ID 
		return childPro.processID;

	}
	

	protected static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 *                              </tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 *                              </tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 *                              </tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
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
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
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
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	private final int MAX_FILES = 16;
	private final int STDINPUT = 0;
	private final int STDOUTPUT = 1;
	public static final int exceptionIllegalSyscall = 100;

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = Config.getInteger("Processor.numStackPages", 8);

	protected int initialPC, initialSP;
	protected int argc, argv;

	protected static final int pageSize = Processor.pageSize;
	protected static final char dbgProcess = 'a';

	protected Lock lock1 = new Lock();

	protected OpenFile[] fileList;


	protected int processID;

	protected OpenFile stdin;

	protected OpenFile stdout;
	
	//PART 3 VARIABLES
		protected UserProcess parentProcess; //hold parent process
		protected LinkedList<UserProcess> childProcesses; //maintain list of child processes
		private static Lock lock; //lock needed for implementation
		protected UThread thread; //thread needed for joining 
		protected HashMap<Integer,Integer> childProcessStatus; //maintain child status
		protected static int counter = 0; //needed to make process ID
		
		

}
