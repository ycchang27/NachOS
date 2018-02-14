package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
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
		waitQueue = new LinkedList<Lock>();
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
		
		Lock waiter = new Lock();
		waitQueue.add(waiter);
		// waitQueue.add(KThread.currentThread());
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
			((Lock)waitQueue.removeFirst()).release();
			//(waitQueue.removeFirst()).ready();
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

	private Lock conditionLock;
	// private LinkedList<Lock> waitQueue;
	private LinkedList<Lock> waitQueue;
}
