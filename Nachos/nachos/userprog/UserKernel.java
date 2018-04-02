package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */

	public int pagesAmount = Machine.processor().getNumPhysPages();

	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

		pageLock = new Lock();
		for (int i = 0; i < pagesAmount; i++) {
			pageTable.add(i);
		}

	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		//System.out.println("Testing the readVirtualMemory with empty pageTable.");
		//System.out.println("Should return error or null");

		//UserProcess process = UserProcess.newUserProcess();
		//String test = process.readVirtualMemoryString(Machine.processor().readRegister(Machine.processor().regA0), 256);
		//System.out.println(test);

		//byte[] reader = new byte[Machine.processor().readRegister(Machine.processor().regA0)];

		//process.numPages = pageTable.size();

		//int test1 = process.writeVirtualMemory(Machine.processor().readRegister(Machine.processor().regA1), reader);
		//System.out.println(test1);

	}

	/**
	 * Returns the current process.
	 *
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 *
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of the
	 * exception (see the <tt>exceptionZZZ</tt> constants in the <tt>Processor</tt>
	 * class). If the exception involves a bad virtual address (e.g. page fault, TLB
	 * miss, read-only, bus error, or address error), the processor's BadVAddr
	 * register identifies the virtual address that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	public static int getPage() {
		int pageNumber = -1;

		Machine.interrupt().disable();
		if (pageTable.isEmpty() == false)
			pageNumber = pageTable.removeFirst();
		Machine.interrupt().enable();
		return pageNumber;
	}

	//add a page to pageTable
	public static void addAPage(int pageNum) {
		Lib.assertTrue(pageNum >= 0 && pageNum < Machine.processor().getNumPhysPages());
		Machine.interrupt().disable();
		pageTable.add(pageNum);
		Machine.interrupt().enable();
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 *
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();

		// pass arguments for coff files here!!
		String[] arguments = { /* insert arguments to pass here */ };
		Lib.assertTrue(process.execute(shellProgram, arguments));

		KThread.currentThread().finish();
	}

	//delete the page from pageTable
	public static boolean deletePage(int ppn) {
		boolean value = false;

		pageLock.acquire();
		pageTable.add(new Integer(ppn));
		value = true;
		pageLock.release();

		return value;
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Number of processes */
	public static int numProcess = 0;

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	private static LinkedList<Integer> pageTable = new LinkedList<Integer>();

	private static Lock pageLock;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
}