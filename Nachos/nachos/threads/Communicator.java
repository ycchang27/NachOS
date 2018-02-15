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
	
	this.waiting = new Lock();
	this.speak = new Condition2(waiting);
	this.listen = new Condition2(waiting);
	this.spoke = false;
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
		this.waiting.acquire(); // acquire lock
		
		//if (!burner)	// burner is false if communicator hasn't been used before
			while (spoke)	// spoke is true if another thread previously called speak(int)
			{
				this.listen.wakeAll(); //wake the listeners
				this.speak.sleep(); // so sleep until that thread is done
			}
		//else
			//burner = false;
		this.toTransfer = word;	// set message to pass
		this.spoke = true;	// flag other speakers to sleep
		this.speak.wake();	// hopefully wake a listener
		this.waiting.release();	// release lock 
		// NOTE: order of messages conveyed is NOT deterministic and actually unlikely to occur sequentially
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */    
	public int listen() {
		this.waiting.acquire();
		//if (!burner)
			while (!spoke)
			{
				this.listen.sleep();
			}
		//else
			///burner = false;
		int transferring = this.toTransfer;
		spoke = false;
		//waitQueue.wake();
		this.speak.wakeAll();
		
		this.waiting.release();
		return transferring;
	}
//variable definitions
	private Lock waiting;
	private Condition2 speak;
	private Condition2 listen;

	private int toTransfer;
	private boolean spoke;
}
