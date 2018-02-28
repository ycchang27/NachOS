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

			if (transferPriority)	// use different comparators to sort set depending on donations enabling
				thread_states = new TreeSet<ThreadState>(new SortByPriority());
			else
				thread_states = new TreeSet<ThreadState>(new SortByEffectivePriority());
		}

		public void overkill()		// update resource owner's effective priority via donations
		{
			if (current_holder != null)
			{
				current_holder.calcEffective();
				for (PriorityQueue p : current_holder.currently_acquired)	// iterate through every resource queue
				{
					if (!p.thread_states.isEmpty())
						for (ThreadState t : p.thread_states)
						{
							t.calcEffective();		// update every thread's effective priority 
							current_holder.offer(t.ePriority, t);		// and donate it to resource owner
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
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			ThreadState t = pickNextThread();
			if (t != null)
			{
				t.acquire(this);		// next thread to run calls acquire
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
			overkill();		// make sure effective priorities are updated
			return thread_states.pollLast();		// remove and return highest effective priority thread
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

			if (currently_waiting != null)		// remove self from TreeSet (for sorting)
			{
				currently_waiting.thread_states.remove(this);
			}
			if (priority > priorityMaximum) this.priority = priorityMaximum;
			else if (priority < priorityMinimum) this.priority = priorityMinimum;
			else this.priority = priority;
			this.ePriority = this.priority;		// default effective priority is base priority
			if (currently_waiting != null)		// add self to TreeSet (for sorting)
			{
				currently_waiting.thread_states.add(this);
			}

			if (currently_waiting != null)		// if in queue for a resource, process donations in queue
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
			if (currently_waiting != null)		// illegal to be in multile queues
			{
				currently_waiting.thread_states.remove(this);
			}
			this.turns_waiting = 0;		// start turns_waiting from 0
			for (ThreadState t : waitQueue.thread_states)		// increment turns of other threads in queue
			{
				t.turns_waiting++;
			}
			waitQueue.thread_states.add(this);
			currently_waiting = waitQueue;
			if (currently_acquired != null && currently_acquired.contains(currently_waiting))		// update set of acquired resources
			{
				currently_acquired.remove(currently_waiting);
			}
			waitQueue.overkill();		// process donations in queue, since a new thread is queued
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
			if (currently_acquired == null) currently_acquired = new HashSet<PriorityQueue>();
			if (currently_waiting != null)	// stop waiting, since thread has acquired it
			{
				currently_waiting.thread_states.remove(this);
				if (currently_waiting == waitQueue)
					currently_waiting = null;
			}
			waitQueue.thread_states.remove(this);
			currently_acquired.add(waitQueue);		// update set of acquired resources

			if (waitQueue.current_holder != null && waitQueue.current_holder != this)	// replace previous resource owner and update its priority
			{
				waitQueue.current_holder.currently_acquired.remove(waitQueue);
				waitQueue.current_holder.calcEffective();
			}
			waitQueue.current_holder = this;

			waitQueue.overkill();		// process donations, since new thread acquired the resource
		}

		public void calcEffective()		// update effective priority for this ThreadState
		{
			reset();		// begin with effective priority = base priority
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

		public boolean offer(int donation, ThreadState donor)		// donation handler
		{
			if (donation > this.ePriority)
			{
				this.ePriority = donation;
				this.donor = donor;
				return true;
			}
			return false;
		}

		public void reset()		// donation helper function
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

	// Comparator classes for use with SortedSet objects in Java
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
	
	public static void selfTest(){return;}
}