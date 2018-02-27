//package nachos.threads;
//
//import nachos.machine.*;
//
//import java.util.LinkedList;
//import java.util.TreeSet;
//import java.util.HashSet;
//import java.util.Iterator;
//import java.util.PriorityQueue;
//
///**
// * A scheduler that chooses threads based on their priorities.
// *
// * <p>
// * A priority scheduler associates a priority with each thread. The next thread
// * to be dequeued is always a thread with priority no less than any other
// * waiting thread's priority. Like a round-robin scheduler, the thread that is
// * dequeued is, among all the threads of the same (highest) priority, the
// * thread that has been waiting longest.
// *
// * <p>
// * Essentially, a priority scheduler gives access in a round-robin fassion to
// * all the highest-priority threads, and ignores all other threads. This has
// * the potential to
// * starve a thread if there's always a thread waiting with higher priority.
// *
// * <p>
// * A priority scheduler must partially solve the priority inversion problem; in
// * particular, priority must be donated through locks, and through joins.
// */
//public class PriorityScheduler extends Scheduler {
//	/**
//	 * Allocate a new priority scheduler.
//	 */
//	public PriorityScheduler() {
//	}
//
//	/**
//	 * Allocate a new priority thread queue.
//	 *
//	 * @param transferPriority <tt>true</tt> if this queue should
//	 * transfer priority from waiting threads
//	 * to the owning thread.
//	 * @return a new priority thread queue.
//	 */
//	public ThreadQueue newThreadQueue(boolean transferPriority) {
//		return new PriorityThreadQueue(transferPriority);
//	}
//
//	public int getPriority(KThread thread) {
//		Lib.assertTrue(Machine.interrupt().disabled());
//
//		return getThreadState(thread).getPriority();
//	}
//
//	public int getEffectivePriority(KThread thread) {
//		Lib.assertTrue(Machine.interrupt().disabled());
//
//		return getThreadState(thread).getEffectivePriority();
//	}
//
//	public void setPriority(KThread thread, int priority) {
//		Lib.assertTrue(Machine.interrupt().disabled());
//
//		Lib.assertTrue(priority >= priorityMinimum &&
//				priority <= priorityMaximum);
//
//		getThreadState(thread).setPriority(priority);
//	}
//
//	public boolean increasePriority() {
//		boolean intStatus = Machine.interrupt().disable();
//
//		KThread thread = KThread.currentThread();
//
//		int priority = getPriority(thread);
//		if (priority == priorityMaximum)
//			return false;
//
//		setPriority(thread, priority+1);
//
//		Machine.interrupt().restore(intStatus);
//		return true;
//	}
//
//	public boolean decreasePriority() {
//		boolean intStatus = Machine.interrupt().disable();
//
//		KThread thread = KThread.currentThread();
//
//		int priority = getPriority(thread);
//		if (priority == priorityMinimum)
//			return false;
//
//		setPriority(thread, priority-1);
//
//		Machine.interrupt().restore(intStatus);
//		return true;
//	}
//
//	/**
//	 * The default priority for a new thread. Do not change this value.
//	 */
//	public static final int priorityDefault = 1;
//	/**
//	 * The minimum priority that a thread can have. Do not change this value.
//	 */
//	public static final int priorityMinimum = 0;
//	/**
//	 * The maximum priority that a thread can have. Do not change this value.
//	 */
//	public static final int priorityMaximum = 7;
//
//	/**
//	 * Return the scheduling state of the specified thread.
//	 *
//	 * @param thread the thread whose scheduling state to return.
//	 * @return the scheduling state of the specified thread.
//	 */
//	protected ThreadState getThreadState(KThread thread) {
//		if (thread.schedulingState == null)
//			thread.schedulingState = new ThreadState(thread);
//
//		return (ThreadState) thread.schedulingState;
//	}
//	
//	public static void selfTest() {
//		System.out.println("---------PriorityScheduler test---------------------");
//		PriorityScheduler s = new PriorityScheduler();
//		ThreadQueue queue = s.newThreadQueue(true);
//		ThreadQueue queue2 = s.newThreadQueue(true);
//		ThreadQueue queue3 = s.newThreadQueue(true);
//		
//		KThread thread1 = new KThread();
//		KThread thread2 = new KThread();
//		KThread thread3 = new KThread();
//		KThread thread4 = new KThread();
//		KThread thread5 = new KThread();
//		thread1.setName("thread1");
//		thread2.setName("thread2");
//		thread3.setName("thread3");
//		thread4.setName("thread4");
//		thread5.setName("thread5");
//
//		
//		boolean intStatus = Machine.interrupt().disable();
//		
//		queue3.acquire(thread1); // thread1 holds queue3
//		queue.acquire(thread1); // thread1 holds queue
//		queue.waitForAccess(thread2); // thread2 waits for queue
//		queue2.acquire(thread4); // thread4 holds queue2
//		queue2.waitForAccess(thread1); // thread1 waits for queue2
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		s.getThreadState(thread2).setPriority(3); // thread2 = 3 -> thread1 = 3 -> thread4 = 3
//		
//		System.out.println("After setting thread2's EP=3:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		queue.waitForAccess(thread3); // thread3 waits for queue
//		s.getThreadState(thread3).setPriority(5); // thread3 = 5 -> thread1 = 5 -> thread4 = 5
//		
//		System.out.println("After adding thread3 with EP=5:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		s.getThreadState(thread3).setPriority(2);
//		
//		System.out.println("After setting thread3 EP=2:"); // thread3 = 2 -> thread1 = 3 -> thread4 = 3
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		System.out.println("Thread1 acquires queue and queue3"); // while thread4 holds queue2
//		
//		Machine.interrupt().restore(intStatus);
//		System.out.println("--------End PriorityScheduler test------------------");
//	}
//
//	/**
//	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
//	 */
//	protected class PriorityThreadQueue extends ThreadQueue {
//		PriorityThreadQueue(boolean transferPriority) {
//			this.transferPriority = transferPriority;
//		}
//
//		public void waitForAccess(KThread thread) {
//			Lib.assertTrue(Machine.interrupt().disabled());
//			getThreadState(thread).waitForAccess(this);
//		}
//
//		public void acquire(KThread thread) {
//			Lib.assertTrue(Machine.interrupt().disabled());
//			getThreadState(thread).acquire(this);
//		}
//		/**We need this function to remove the highest priority thread from the wait queue.
//		 * once it is removed calculate its effective priority(which can depend on multiple waitqueues
//		 * @return HighestPriority KThread
//		 */
//		public KThread nextThread() {
//			Lib.assertTrue(Machine.interrupt().disabled());
//			ThreadState threadState = this.pickNextThread();
//			if (threadState != null){
//				//System.out.println(threadState.thread.toString());
//				//System.out.println(threadState.age);
//			}
//			priorityQueue.remove(threadState);
//			if (transferPriority && threadState != null) {
//				this.dequeuedThread.removeQueue(this);
//				threadState.waiting = null;
//				threadState.addQueue(this);
//			}
//			this.dequeuedThread = threadState;
//			if (threadState == null){
//				this.priorityQueue = new PriorityQueue<ThreadState>();
//				return null;
//			}
//			return threadState.thread;
//		}
//
//		/**
//		 * Return the next thread that <tt>nextThread()</tt> would return,
//		 * without modifying the state of this queue.
//		 *
//		 * @return the next thread that <tt>nextThread()</tt> would
//		 * return.
//		 */
//		protected ThreadState pickNextThread() {
//			boolean intStatus = Machine.interrupt().disable();
//
//			//ensure priorityQueue is properly ordered
//			//does this take the old priorityQueue and reorder it? YES!!!
//			this.priorityQueue = new PriorityQueue<ThreadState>(priorityQueue);
//
//			Machine.interrupt().restore(intStatus);
//			return this.priorityQueue.peek();
//		}
//		public void print() {
//			Lib.assertTrue(Machine.interrupt().disabled());
//			// implement me (if you want)
//		}
//
//		/**
//		 * The base priority queue object.
//		 */
//		protected PriorityQueue<ThreadState> priorityQueue = new PriorityQueue<ThreadState>();
//		/** The most recently dequeued ThreadState. */
//		protected ThreadState dequeuedThread = null;
//		/**
//		 * <tt>true</tt> if this queue should transfer priority from waiting
//		 * threads to the owning thread.
//		 */
//		public boolean transferPriority;
//	}
//
//	/**
//	 * The scheduling state of a thread. This should include the thread's
//	 * priority, its effective priority, any objects it owns, and the queue
//	 * it's waiting for, if any.
//	 *
//	 * @see nachos.threads.KThread#schedulingState
//	 */
//	protected class ThreadState implements Comparable<ThreadState> {
//		/**
//		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
//		 * specified thread.
//		 *
//		 * @param thread the thread this state belongs to.
//		 */
//		public ThreadState(KThread thread) {
//			this.thread = thread;
//			//initialize the onQueue linkedlist
//			this.onQueues = new LinkedList<PriorityThreadQueue>();
//			this.age = Machine.timer().getTime();
//			this.effectivePriority = priorityDefault;
//			this.waiting = null;
//		}
//
//		/**
//		 * Return the priority of the associated thread.
//		 *
//		 * @return the priority of the associated thread.
//		 */
//		public int getPriority() {
//			return priority;
//		}
//		/**
//		 * Calculate the Effective Priority of a thread and the thread that currently holds the resource
//		 * it is waiting on.
//		 */
//		public void calcEffectivePriority() {
//			int initialPriority = this.getPriority();
//			int maxEP = -1;
//			if (onQueues.size() != 0){
//				//System.out.println(this.thread+", EP="+this.getEffectivePriority());
//				int size = onQueues.size();
//				//System.out.println(size);
//				for(int i = 0; i < size; i++){
//					PriorityThreadQueue current = onQueues.get(i);
//					ThreadState donator = current.pickNextThread();
//					if (donator != null){
//						//System.out.println(donator.thread+", EP="+donator.getEffectivePriority());
//						if ((donator.getEffectivePriority() > maxEP) && current.transferPriority)
//							maxEP = donator.getEffectivePriority();
//					}
//				}
//			}
//			if (initialPriority > maxEP){
//				maxEP = initialPriority;
//			}
//			this.effectivePriority = maxEP;
//			//System.out.println(this.effectivePriority);
//			//now that my own effectivePriority Changes I have to recalculate the threads which i am waiting on
//			if (this.waiting != null && this.waiting.dequeuedThread != null){
//				if (this.effectivePriority != this.waiting.dequeuedThread.effectivePriority){
//					this.waiting.dequeuedThread.calcEffectivePriority();
//				}
//			};
//			//System.out.println(this.effectivePriority);
//		}
//
//		public int getEffectivePriority() {
//			return this.effectivePriority;
//		}
//
//		/**
//		 * Set the priority of the associated thread to the specified value.
//		 *
//		 * @param priority the new priority.
//		 */
//		public void setPriority(int priority) {
//			if (this.priority == priority)
//				return;
//			//Pretty sure we doon't need following line since nothing should be accessing effectivePriority directly
//			//this.effectivePriority = effectivePriority - (this.priority - priority);
//			this.priority = priority;
//			this.calcEffectivePriority();
//			if(this.waiting != null && this.waiting.dequeuedThread != null)
//				this.waiting.dequeuedThread.calcEffectivePriority();
//			//this.waiting.dequeuedThread.calcEffectivePriority();
//		}
//
//		/**
//		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
//		 * the associated thread) is invoked on the specified priority queue.
//		 * The associated thread is therefore waiting for access to the
//		 * resource guarded by <tt>waitQueue</tt>. This method is only called
//		 * if the associated thread cannot immediately obtain access.
//		 *
//		 * @param waitQueue the queue that the associated thread is
//		 * now waiting on.
//		 *
//		 * @see nachos.threads.ThreadQueue#waitForAccess
//		 */
//		public void waitForAccess(PriorityThreadQueue waitQueue) {
//			Lib.assertTrue(Machine.interrupt().disabled());
//			long time = Machine.timer().getTime();
//			this.age = time;
//			waitQueue.priorityQueue.add(this);
//			this.waiting = waitQueue;
//			this.calcEffectivePriority();
//		}
//
//		/**
//		 * Called when the associated thread has acquired access to whatever is
//		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
//		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
//		 * <tt>thread</tt> is the associated thread), or as a result of
//		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
//		 *
//		 * @see nachos.threads.ThreadQueue#acquire
//		 * @see nachos.threads.ThreadQueue#nextThread
//		 */
//		public void acquire(PriorityThreadQueue waitQueue) {
//			//Seems good, checks to see if queue is empty, if it is just make it dequeued thread.
//			//needs to add waitQueue
//			Lib.assertTrue(Machine.interrupt().disabled());
//			Lib.assertTrue(waitQueue.priorityQueue.isEmpty());
//			waitQueue.dequeuedThread = this;
//			this.addQueue(waitQueue);
//			this.calcEffectivePriority();
//		}
//
//		public int compareTo(ThreadState threadState){
//			//changed first if from > to <
//			if (threadState == null)
//				return -1;
//			if (this.getEffectivePriority() < threadState.getEffectivePriority()){
//				return 1;
//			}else{ if (this.getEffectivePriority() > threadState.getEffectivePriority()){
//				return -1;
//			}else{
//				if (this.age >= threadState.age)
//					return 1;
//				else{ return -1; }
//			}
//			}
//		}
//
//		public void removeQueue(PriorityThreadQueue queue){
//			onQueues.remove(queue);
//			this.calcEffectivePriority();
//		}
//		public void addQueue(PriorityThreadQueue queue){
//			onQueues.add(queue);
//			this.calcEffectivePriority();
//		}
//
//		public String toString() {
//			return "ThreadState thread=" + thread + ", priority=" + getPriority() + ", effective priority=" + getEffectivePriority();
//		}
//		/** The thread with which this object is associated. */
//		protected KThread thread;
//		/** The priority of the associated thread. */
//		protected int priority = priorityDefault;
//		/** The effective priority of the associated thread.
//		 * Every time the effectivePriority is used it will be recalculated
//		 * Might not need this actually!!!*/
//		//protected int effectivePriority = calculateEffective();
//		/** The age of the thread state relative to Nachos time. */
//		public long age = Machine.timer().getTime();
//		/** a linkedlist representing all the waitqueues it is getting priority from.*/
//		protected LinkedList<PriorityThreadQueue> onQueues;
//		protected int effectivePriority;
//		protected PriorityThreadQueue waiting;
//
//	}
//}

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

		public void overkill()
		{
			if (current_holder != null)
			{
				current_holder.calcEffective();
				for (PriorityQueue p : current_holder.currently_acquired)
				{
					if (!p.thread_states.isEmpty())
						for (ThreadState t : p.thread_states)
						{
							t.calcEffective();
							current_holder.offer(t.ePriority, t);
						}
				}
			}
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
			overkill();
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
			overkill();
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
			overkill();
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

			// regarding donations
			//			if (currently_waiting != null)
			//			{
			//				if (currently_waiting.current_holder != null &&
			//						currently_waiting.current_holder.donor == this)
			//				{
			//					currently_waiting.current_holder.calcEffective();
			//				}
			//				else if (currently_waiting.current_holder != null)
			//				{
			//					currently_waiting.current_holder.offer(priority, this);
			//				}
			//			}
			if (currently_waiting != null)
				currently_waiting.overkill();
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
			//			if (waitQueue.current_holder != null)
			//				waitQueue.current_holder.offer(this.ePriority, this);
			waitQueue.overkill();
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
				if (currently_waiting == waitQueue)
					currently_waiting = null;
			}
			waitQueue.thread_states.remove(this);
			currently_acquired.add(waitQueue);

			// regarding donations:
			if (waitQueue.current_holder != null && waitQueue.current_holder != this)
			{
				waitQueue.current_holder.currently_acquired.remove(waitQueue);
				waitQueue.current_holder.calcEffective();
			}
			waitQueue.current_holder = this;

			//			for (ThreadState t : waitQueue.thread_states)
			//			{
			//				offer(t.getEffectivePriority(), t);
			//			}
			waitQueue.overkill();
		}

		// notes: iterate through currently_acquired and see
		public void calcEffective()
		{
			reset();
			if (currently_acquired != null)
				for (PriorityQueue Q : currently_acquired)
				{
					for (ThreadState t : Q.thread_states)
					{
						if (offer(t.getEffectivePriority(), t))
							if (currently_waiting != null && currently_waiting.current_holder != null)
								currently_waiting.current_holder.calcEffective();
					}
				}
		}

		public boolean offer(int donation, ThreadState donor)
		{
			if (donation > this.ePriority)
			{
				this.ePriority = donation;
				this.donor = donor;
				return true;
			}
			return false;
		}

		public void reset()
		{
			this.ePriority = this.priority;
		}

		/** The thread with which this object is associated. */	   
		public KThread thread;
		public ThreadState donor;
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
	//    public static void selfTest(){return;}

	public static void selfTest() {
		System.out.println("---------PriorityScheduler test---------------------");
		PriorityScheduler s = new PriorityScheduler();
		ThreadQueue queue = s.newThreadQueue(true);
		ThreadQueue queue2 = s.newThreadQueue(true);
		ThreadQueue queue3 = s.newThreadQueue(true);

		KThread thread1 = new KThread();
		KThread thread2 = new KThread();
		KThread thread3 = new KThread();
		KThread thread4 = new KThread();
		KThread thread5 = new KThread();
		thread1.setName("thread1");
		thread2.setName("thread2");
		thread3.setName("thread3");
		thread4.setName("thread4");
		thread5.setName("thread5");


		boolean intStatus = Machine.interrupt().disable();

		queue3.acquire(thread1); // thread1 holds queue3
		queue.acquire(thread1); // thread1 holds queue
		queue.waitForAccess(thread2); // thread2 waits for queue
		queue2.acquire(thread4); // thread4 holds queue2
		queue2.waitForAccess(thread1); // thread1 waits for queue2
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());

		s.getThreadState(thread2).setPriority(3); // thread2 = 3 -> thread1 = 3 -> thread4 = 3

		System.out.println("After setting thread2's EP=3:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());

		queue.waitForAccess(thread3); // thread3 waits for queue
		s.getThreadState(thread3).setPriority(5); // thread3 = 5 -> thread1 = 5 -> thread4 = 5

		System.out.println("After adding thread3 with EP=5:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());

		s.getThreadState(thread3).setPriority(2);

		System.out.println("After setting thread3 EP=2:"); // thread3 = 2 -> thread1 = 3 -> thread4 = 3
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());

		System.out.println("Thread1 acquires queue and queue3"); // while thread4 holds queue2

		Machine.interrupt().restore(intStatus);
		System.out.println("--------End PriorityScheduler test------------------");
	}
}
