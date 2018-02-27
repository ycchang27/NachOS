package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.HashSet;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}
	
	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		Lib.assertTrue(priority >= priorityMinimum &&
			   priority <= priorityMaximum);
		
		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				   
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				   
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			
			if (transferPriority)
				thread_states = new TreeSet<ThreadState>(new SortByPriority());
			else
				thread_states = new TreeSet<ThreadState>(new SortByEffectivePriority());
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}


		// return null if no threads are queued
		// next thread to be ran should call threadstate.acquire(queue)
		// if donation is enabled, do not consider scheduling self to be next (uncertain)
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			ThreadState t = pickNextThread();
			if (t != null)
			{
				t.acquire(this);
				return t.thread;
			}
			return null;
		}

		// return thread with highest effective priority
		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			return thread_states.pollLast();
		}
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		TreeSet<ThreadState> thread_states;
		ThreadState current_holder;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			
			setPriority(priorityDefault);
			this.ePriority = this.priority;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return ePriority;
		}

		// make sure queue is ordered properly after this function is called
		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
			return;
			
			if (currently_waiting != null)
			{
				currently_waiting.thread_states.remove(this);
			}
			this.priority = priority;
			this.ePriority = this.priority;
			if (currently_waiting != null)
			{
				currently_waiting.thread_states.add(this);
			}
			
			// implement me
		}

		// add thread to waitQueue
		// make sure waitQueue is ordered properly after adding thread
		// save a reference to waitQueue passed
		// note that this thread has yet to acquire queue's resource
		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			if (currently_waiting != null)
			{
				currently_waiting.thread_states.remove(this); // mine: replace old waitQueue with new one; hers: ignore new one
			}
			this.turns_waiting = 0;
			for (ThreadState t : waitQueue.thread_states)
			{
				t.turns_waiting++;
			}
			waitQueue.thread_states.add(this);
			currently_waiting = waitQueue;
			if (currently_acquired != null && currently_acquired.contains(currently_waiting))
			{
				currently_acquired.remove(currently_waiting);
			}
			
			// regarding donations
			calcEffective(waitQueue);
		}

		// save reference to queue, noting that thread has acquired its resource
		// if thread was previously waiting in queue for this resource, stop waiting
		// if thread is also in queue for a different resource, dequeue from it
		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			if (currently_acquired == null) currently_acquired = new HashSet<PriorityQueue>();
			if (currently_waiting != null)
			{
				currently_waiting.thread_states.remove(this);
				currently_waiting = null;
			}
			waitQueue.thread_states.remove(this);
			currently_acquired.add(waitQueue);
			
			// regarding donations:
			if (waitQueue.current_holder != null)
			{
				waitQueue.current_holder.reset();
				waitQueue.current_holder.currently_acquired.remove(waitQueue);
			}
			waitQueue.current_holder = this;
			calcEffective(waitQueue);
		}
		
		// notes: iterate through currently_acquired and see

		public void calcEffective(PriorityQueue acquiringQueue)
		{
			if (acquiringQueue.current_holder == null || acquiringQueue.thread_states.isEmpty()) return;
			ThreadState possible_donor = acquiringQueue.thread_states.last();
			if (possible_donor.getPriority() > acquiringQueue.current_holder.getPriority())
			{
				acquiringQueue.current_holder.ePriority = possible_donor.getPriority();
			}
		}
		
		public void reset()
		{
			this.ePriority = this.priority;
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		public int ePriority;
		public int turns_waiting;

		PriorityQueue currently_waiting;
		HashSet<PriorityQueue> currently_acquired;
	}
	
	class SortByPriority implements Comparator<ThreadState>
	{
		@Override
		public int compare(ThreadState o1, ThreadState o2) {
			if (o1.getPriority() == o2.getPriority()) return o1.turns_waiting - o2.turns_waiting;
			else return o1.getPriority() - o2.getPriority();
		}
	}
	
	class SortByEffectivePriority implements Comparator<ThreadState>
	{

		@Override
		public int compare(ThreadState arg0, ThreadState arg1) {
			if (arg0.getEffectivePriority() == arg1.getEffectivePriority()) return arg0.turns_waiting - arg1.turns_waiting;
			else return arg0.getEffectivePriority() - arg1.getEffectivePriority();
		}
		
	}
    
    private static void Test1() {
        System.out.println("// Test 1: Same Priority");
        KThread kt[] = new KThread[5];
        for (int i = 0; i < kt.length; i++) {
            kt[i] = new KThread(new Runnable1(i));
            boolean intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(kt[i], 1);
            Machine.interrupt().restore(intStatus);
            kt[i].fork();
        }
        for (int i = 0; i < kt.length; i++)
            kt[i].join();
    }
    
    private static void Test2() {
        System.out.println("// Test 2: Different Priorities");
        KThread kt[] = new KThread[5];
        for (int i = 0; i < kt.length; i++) {
            kt[i] = new KThread(new Runnable1(i));
            boolean intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(kt[i], 7-i);
            Machine.interrupt().restore(intStatus);
            kt[i].fork();
        }
        for (int i = 0; i < kt.length; i++)
            kt[i].join();
    }
    
    private static void Test3() {
        System.out.println("// Test 3: Priority Donation with lock");
        Lock lock = new Lock();
        KThread low = new KThread(new LowPriority(lock));
        KThread med = new KThread(new MediumPriority(0));
        KThread high = new KThread(new HighPriority(lock));
        boolean intStatus = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(low,1);
        ThreadedKernel.scheduler.setPriority(med,3);
        ThreadedKernel.scheduler.setPriority(high,5);
        Machine.interrupt().restore(intStatus);
        low.fork();
        KThread.yield(); // run low first, so it grabs lock
        med.fork(); // queue med
        high.fork(); // queue high, which is queuing for lock, so donate to low
        KThread.yield(); // run low first
        low.join(); // joins low first, releasing lock
        med.join(); // joins last
        high.join(); // joins high second, after lock is released
    }
    
    private static void Test4() {
        System.out.println("// Test 4: Threads with multiple locks");
        Lock lock1 = new Lock();
        Lock lock2 = new Lock();
        Lock lock3 = new Lock();
        KThread k1 = new KThread(new LockThread(0, lock1)); // runs before k2
        KThread k2 = new KThread(new LockThread(1, lock1, lock2)); // runs before k4
        KThread k3 = new KThread(new LockThread(2, lock1, lock3)); // grabs l3 first
        KThread k4 = new KThread(new LockThread(3, lock2, lock3)); // runs after k3
        KThread k5 = new KThread(new LockThread(4, lock3)); // donates priority 3 to k3
        k1.fork();
        k2.fork();
        k3.fork();
        k4.fork();
        k5.fork();
        KThread.yield();
        boolean intStatus = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(k5,3);
        Machine.interrupt().restore(intStatus);
        KThread.yield();
        k1.join();
        k2.join();
        k3.join();
        k4.join();
        k5.join();
    }
    
    private static void Test5()
    {
    	System.out.println("// Test 5: B's test");
    	Lock mLock = new Lock();
    	Lock nLock = new Lock();
    	KThread mThreads[] = new KThread[5];
    	mThreads[0] = new KThread(new LockThread(0, mLock));
    	mThreads[1] = new KThread(new LockThread(1, mLock));
    	mThreads[2] = new KThread(new LockThread(2, mLock));
    	mThreads[3] = new KThread(new LockThread(3, nLock));
    	mThreads[4] = new KThread(new LockThread(4, mLock));
    	boolean intStatus = Machine.interrupt().disable();
    	mThreads[0].fork();
    	mThreads[1].fork();
    	mThreads[2].fork();
    	mThreads[3].fork();
    	mThreads[4].fork();
    	ThreadedKernel.scheduler.setPriority(mThreads[3], 3);
    	Machine.interrupt().restore(intStatus);
//    	KThread.yield();
    	mThreads[4].join();
    	mThreads[1].join();
    	mThreads[0].join();
    	mThreads[2].join();
    	mThreads[3].join();
    }
    
    private static class Runnable1 implements Runnable {
        Runnable1(int n) {
            this.n = n;
        }
        public void run() {
            for (int i = 0; i < 2; i++) {
                System.out.println("Thread " + n);
                KThread.yield();
            }
        }
        private int n;
    }
    
    private static class LowPriority implements Runnable {
        LowPriority(Lock lock) {
            this.lock = lock;
        }
        public void run() {
            System.out.println("Low priority start");
            lock.acquire();
            KThread.yield();
            System.out.println("Low priority end");
            lock.release();
        }
        private Lock lock;
    }
    
    private static class MediumPriority implements Runnable {
        MediumPriority(int n) {
            this.n = n;
        }
        public void run() {
            KThread.yield();
            System.out.println("Medium priority complete");
        }
        private int n;
    }
    
    private static class HighPriority implements Runnable {
        HighPriority(Lock lock) {
            this.lock = lock;
        }
        public void run() {
            System.out.println("High priority sleep");
            lock.acquire();
            System.out.println("High priority wake");
            lock.release();
        }
        private Lock lock;
    }
    
    private static class LockThread implements Runnable {
        LockThread(int n, Lock lock) {
            this.n = n;
            this.lock = lock;
        }
        LockThread(int n, Lock lock, Lock lock2) {
            this.n = n;
            this.lock = lock;
            this.lock2 = lock2;
        }
        public void run() {
            System.out.println("Thread " + n + " sleep");
            lock.acquire();
            if (lock2 != null)
                lock2.acquire();
            KThread.yield();
            System.out.println("Thread " + n + " wake");
            lock.release();
            if (lock2 != null)
                lock2.release();
        }
        
        private int n;
        private Lock lock;
        private Lock lock2;
    }
    
    public static void selfTest() {
        System.out.println("//** Priority Scheduler Testing Begin **//");
        Test1();
        Test2();
        Test3();
        Test4();
        Test5();
        System.out.println("//** Priority Scheduler Testing End **//");
    }
}
