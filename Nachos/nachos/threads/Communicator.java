package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param	word	the integer to transfer.
	 */
	public void speak(int word) {
		waiting.acquire(); // acquire lock
		if (!burner)	// burner is false if communicator hasn't been used before
			while (spoke)	// spoke is true if another thread previously called speak(int)
			{
				waitQueue.sleep(); // so sleep until that thread is done
			}
		else
			burner = false;
		toTransfer = word;	// set message to pass
		spoke = true;	// flag other speakers to sleep
		waitQueue.wake();	// hopefully wake a listener
		waiting.release();	// release lock 
		// NOTE: order of messages conveyed is NOT deterministic and actually unlikely to occur sequentially
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */    
	public int listen() {
		waiting.acquire();
		if (!burner)
			while (!spoke)
			{
				waitQueue.sleep();
			}
		else
			burner = false;
		int transferring = toTransfer;
		spoke = false;
		waitQueue.wake();
		waiting.release();
		return transferring;
	}

	private Lock waiting;
	private Condition2 waitQueue = new Condition2(waiting);
	private int toTransfer = 0;
	private boolean spoke, burner = true;
}
