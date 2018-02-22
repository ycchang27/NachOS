package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.TreeSet;
//import java.util.HashSet; // Hash tables are difficult to sort
import java.util.Iterator; 

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

		// Tree container for ThreadState objects sorted by effective priority and wait time
		// ... for sorting info, see custom Comparator classes below
		public TreeSet<ThreadState> thread_states;

		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			if (transferPriority)
			{
				// use Comparator SortByEffectivePriority
				thread_states = new TreeSet<ThreadState>(new SortByEffectivePriority());
			}
			else
			{
				// use Comparator SortByPriority
				thread_states  = new TreeSet<ThreadState>(new SortByPriority());
			}
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			ThreadState n = pickNextThread(); // implemented below
			if (n == null) return null;	// if no ThreadState was returned, then no thread is waiting (or error)

			// increment all other thread's waiting time
			for (ThreadState t : thread_states)
			{
				t.turns_waiting++;
			}
			
			return n.thread; // returns ThreadState's associated thread
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			return thread_states.pollLast(); // returns ThreadState with highest (effective) value or null, if set is empty
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
			int effective_priority = priority + donor; // base priority + donations
			// return (effective_priority < priorityMaximum)? ((effective_priority > priorityMinimum)? effective_priority : priorityMinimum) : priorityMaximum;
			if (effective_priority < priorityMaximum)
			{
				if (effective_priority > priorityMinimum)
				{
					return effective_priority;
				}
				else
				{
					return priorityMinimum;
				}
			}
			else
			{
				return priorityMaximum;
			}
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			// implement me
			// this.priority = (priority < priorityMaximum)? ((priority > priorityMinimum)? priority: priorityMinimum) : priorityMaximum;
			if (priority < priorityMaximum)
			{
				if (priority > priorityMinimum)
				{
					this.priority = priority;
				}
				else
				{
					this.priority = priorityMinimum;
				}
			}
			else
			{
				this.priority = priorityMaximum;
			}
		}

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
			this.turns_waiting = 0; // start waiting count at 0
			ThreadState lowest = waitQueue.thread_states.pollFirst(); // fetch & remove lowest priority thread in queue
			if (lowest != null)
			{
				this.donor -= this.priority*(0.8); // since thread is waiting, donate majority of its priority ...
				lowest.donor -= this.donor; // ... to the lowest priority thread
				waitQueue.thread_states.add(lowest); // return it to the queue
			}
			if (!waitQueue.thread_states.contains(this)) waitQueue.thread_states.add(this); // add self to queue
		}

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
			this.donor = 0; // no need to donate after acquiring resources
			this.turns_waiting = 0; // finished waiting or never waited
			if (waitQueue.thread_states.contains(this)) waitQueue.thread_states.remove(this); // if self is on queue, remove self from wait queue
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		public int donor = 0;
		public int turns_waiting = 0; // initialize just in case
	}

	// Comparator objects
	class SortByEffectivePriority implements Comparator<ThreadState>
	{
		public int compare(ThreadState a, ThreadState b)
		{
			// sort in ascending order of effective priority and accumulated wait time
			return 128*(a.getEffectivePriority() - b.getEffectivePriority()) + (a.turns_waiting - b.turns_waiting);
		}
	}
	class SortByPriority implements Comparator<ThreadState>
	{
		public int compare(ThreadState a, ThreadState b)
		{
			// sort in ascending order of base priority and accumulated wait time
			return 128*(a.getPriority() - b.getPriority()) + (a.turns_waiting - b.turns_waiting);
		}
	}
}
