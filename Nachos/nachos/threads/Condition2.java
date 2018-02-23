package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *S
 * @see	nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param	conditionLock	the lock associated with this condition
	 *				variable. The current thread must hold this
	 *				lock whenever it uses <tt>sleep()</tt>,
	 *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */

	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		// waitQueue = new LinkedList<Lock>();
		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The
	 * current thread must hold the associated lock. The thread will
	 * automatically reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean status = Machine.interrupt().disable(); 
		
		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		KThread.sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(status);
		
//		Lock waiter = new Lock();
//		waitQueue.add(waiter);
//
//		conditionLock.release();
//		waiter.acquire();
//		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean status = Machine.interrupt().disable(); 
		
		if (!waitQueue.isEmpty())
		{
			// ((Lock)waitQueue.removeFirst()).release();
			(waitQueue.removeFirst()).ready();
		}
		
		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean status = Machine.interrupt().disable(); 
		
		while (!waitQueue.isEmpty())
		{
			wake();
		}
		Machine.interrupt().restore(status);
	}

	public static void selfTest()
	{     //used for testing wake and wakeall
       System.out.println("--------------Testing Condition 2 ------------------");
       
       //Variables for testing functions
       final Lock lock = new Lock();
       final Condition2 con2 = new Condition2(lock);
       
       KThread sleep = new KThread(new Runnable()
       {
    //Test 1: Sleep
      public void run()
       {
    	  //get the Lock
    	   lock.acquire();
    	   
    	   System.out.println("TESTING SLEEP"); 
    	   System.out.println("Test 1:\n...Going to sleep.....\n");
    	   con2.sleep();
    	   System.out.println("Test 1 Complete: Woke up!\n");
    	   lock.release();
       }
       
    });
       
       sleep.fork();
      
		KThread wake =	new KThread(new Runnable()
		{
		//Test 2: Wake
           public void run()
           {
        	   lock.acquire();
        	   System.out.println("TESTING WAKE"); 
               System.out.println("Test 2:\n...Waking a thread...\n");
               con2.wake();      
				System.out.println("Test 2 Complete: Waking Up!");
				lock.release();
       } } );
		wake.fork();
		sleep.join();
		
		System.out.println("\nTEST 3: SLEEP AND WAKEALL");
		KThread sleep1 =	new KThread(new Runnable()
		{
		//Test 3: Wake All sleeping thread 1
           public void run()
           {
        	   lock.acquire();
               System.out.println("\n...Sleep1 going to sleep...\n");
               con2.sleep();      
				System.out.println("Test 3: Sleep1 waking up!");
				lock.release();
       } } );
		sleep1.fork();
		
		KThread sleep2 =	new KThread(new Runnable()
		{
		//Test 3: Wake All sleeping thead 2
           public void run()
           {
        	   lock.acquire();
               System.out.println("\n...Sleep2 going to sleep...\n");
               con2.sleep();      
				System.out.println("Test 3: Sleep2 waking up!");

				System.out.println("Test 3 Complete: Everyone is awake!");
				lock.release();
       } } );
		sleep2.fork();
		
		
		KThread wakeall =	new KThread(new Runnable()
		{
		//Test 3: Wake all
           public void run()
           {
        	   lock.acquire();
        	   System.out.println("TESTING WAKEALL"); 
               System.out.println("\n...Waking all sleeping threads...\n");
               con2.wakeAll();    
				lock.release();
       } } );
		wakeall.fork();


	}

	
	private Lock conditionLock;
	// private LinkedList<Lock> waitQueue;
	private LinkedList<KThread> waitQueue;
}
