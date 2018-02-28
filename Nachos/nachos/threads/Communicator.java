package nachos.threads;

import nachos.machine.*;
import java.util.Queue;
import java.util.LinkedList;


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
	public void speak(int word) 
	{
		this.waiting.acquire(); // acquire lock
		
		while (spoke)	// spoke is true if another thread previously called speak(int)
		{
			this.listen.wakeAll(); //wake the listeners
			this.speak.sleep(); // so sleep until that thread is done
		}

		//else
		//burner = false;
		this.toTransfer = word;	// set message to pass
		this.spoke = true;	// flag other speakers to sleep
		this.listen.wakeAll();
		this.speak.sleep();	// hopefully wake a listener
		this.waiting.release();	// release lock 
		// NOTE: order of messages conveyed is NOT deterministic and actually unlikely to occur sequentially
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */    
	public int listen() 
	{
		this.waiting.acquire();
		while (!spoke)
		{
			this.listen.sleep();
		}

		int transferring = this.toTransfer;

		this.speak.wakeAll();
		spoke = false;
		//waitQueue.wake();
		this.waiting.release();
		return transferring;
	}
//variable definitions
	private Lock waiting;
	private Condition2 speak;
	private Condition2 listen;

	private int toTransfer;
	private boolean spoke;
	
	
	
	//this selftest has been added to the ThreadedKernal.java file
		public static void selfTest()
		{

		
			KThread t1 = new KThread(new ComTest(1));
			KThread t2 = new KThread(new ComTest(2));
			KThread t3 = new KThread(new ComTest(3));
			KThread t4 = new KThread(new ComTest(4));
			KThread t5 = new KThread(new ComTest(5));

			t1.fork();
			t2.fork();
			t3.fork();
			t4.fork();
			t5.fork();
			
			//run the test
			System.out.println("-----Communicator Test---------");
			new ComTest(0).run();
		}
		
		
	protected static class ComTest implements Runnable
	{
	
	 private int comID;
	 
	 private static Communicator comm = new Communicator();
	 
	 // Construct the object. Pass the comID of the thread plus any variables you
	 // want to share between threads. You may want to pass a KThread as a global
	 // variable to test join.
		ComTest(int comID) 
		{
		    this.comID = comID;
		}
		

		public void run() {
		    // Use an if statement to make the different threads execute different
		    // code.
		    if (comID == 0) 
		    {
		        for (int i = 0; i < 5; i++) 
		        {
		            System.out.println("ComTest " + comID + " Speak(" + i + ")");
		            comm.speak(i);
		        }
		    }
		    else 
		    {
		        for (int i = 0; i < 5; i++) 
		        {
		            System.out.println("ComTest " + comID + " listening to... " + i);
		            int transfered = comm.listen();
		            System.out.println("ComTest " + comID + " heard word " + transfered);
		        }
		    }
		    
		    if (comID == 0)
		    	System.out.println("-----Communicator Test Complete-------");
		    ThreadedKernel.alarm.waitUntil(2000);
		    
		

		}
	}


	
}
