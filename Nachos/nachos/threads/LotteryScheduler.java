package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;


/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer tickets from waiting threads
	 *					to the owning thread.
	 * @return	a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityThreadQueue(transferPriority);
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
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;

	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

//	public static void selfTest() {
//		System.out.println("---------LotteryScheduler test---------------------");
//		LotteryScheduler s = new LotteryScheduler();
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
//		queue.acquire(thread1);
//		queue.waitForAccess(thread2);
//		queue.waitForAccess(thread3);
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("~~~~~~~~Thread4 aquires queue2 thread1 waits~~~~~~~~~`");
//		queue2.acquire(thread4);
//		queue2.waitForAccess(thread1);
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("~~~~~~~~thread2 priority changed to 2~~~~~~~~~`");
//		s.getThreadState(thread2).setPriority(2);
//
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("~~~~~~~~thread2 priority changed to 1~~~~~~~~~`");
//		s.getThreadState(thread2).setPriority(1);
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("~~~~~~~~Thread5 waits on queue1~~~~~~~~~`");
//		queue.waitForAccess(thread5);
//
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("~~~~~~~~thread2 priority changed to 8~~~~~~~~~`");
//		s.getThreadState(thread2).setPriority(8);
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		ThreadQueue newQueue;
//
//		KThread thread10;
//		int tot10 = 0;
//		KThread thread20;
//		int tot20 = 0;
//		for (int i =0; i<999; i++){
//			newQueue = s.newThreadQueue(true);
//			thread10 = new KThread();
//			thread20 = new KThread();
//			newQueue.waitForAccess(thread10);
//			newQueue.waitForAccess(thread20);
//			if (newQueue.nextThread() == thread10)
//				tot10 += 1;
//			else
//				tot20+=1;	
//		}
//
//		System.out.println("thread1 Total = " + tot10);
//		System.out.println("thread2 Total = " + tot20);
//		/*
//		queue3.acquire(thread5);
//		queue3.waitForAccess(thread4);
//		System.out.println("thread5 EP="+s.getThreadState(thread5).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//
//		queue2.nextThread();
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread5 EP="+s.getThreadState(thread5).getEffectivePriority());
//		 */
//	}

	protected class PriorityThreadQueue extends ThreadQueue{
		PriorityThreadQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			this.dequeuedThread = null;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void addToQueue(ThreadState threadState) {
			this.priorityQueue.add(threadState);
			//this.priorityQueue = new PriorityQueue<ThreadState>(priorityQueue);

		}

		public void removeFromQueue(ThreadState threadState) {
			this.priorityQueue.remove(threadState);
			//this.priorityQueue = new PriorityQueue<ThreadState>(priorityQueue);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}
		/**We need this function to remove the highest priority thread from the wait queue.
		 * once it is removed calculate its effective priority(which can depend on multiple waitqueues
		 * @return HighestPriority KThread
		 */
		public KThread nextThread(){
			//Create a counter for total number of tickets
			int totTickets = 0;
			//create a LinkedList of threads and another of the tickets associated with that thread
			LinkedList<ThreadState> threads = new LinkedList<ThreadState>();
			LinkedList<Integer> tickets = new LinkedList<Integer>();
			//Iterate through the threadStates waiting to acquire the resource
			//store their thread and effective priority
			Iterator<ThreadState> queue = priorityQueue.iterator();
			for (int i = 0; queue.hasNext(); i++){
				ThreadState current = queue.next();
				int ticketNum = (Integer)current.getEffectivePriority();
				totTickets += ticketNum;
				threads.add(current);
				tickets.add(ticketNum);
			}
			//set return thread to null for now
			ThreadState pickedThread = null;
			boolean notFound = true;
			int ticketsSoFar = 0;
			//if some threads exist go through all of the threads and if the random
			//ticket falls in the range of the thread give pickedThread to that thread
			if (totTickets > 0){
				Integer ticketChoice = generator.nextInt(totTickets);
				for(int j = 0; notFound && j < tickets.size(); j++){
					ticketsSoFar += tickets.get(j);
					if ((ticketChoice - ticketsSoFar) <= -1){
						pickedThread = threads.get(j);
						notFound = false;
					}
				}
			}
			if (transferPriority && pickedThread != null) {
				if (this.dequeuedThread != null) {
					this.dequeuedThread.removeQueue(this);
				}
				pickedThread.waiting = null;
				pickedThread.addQueue(this);
			}
			this.dequeuedThread = pickedThread;
			if (this.dequeuedThread != null){
				this.priorityQueue.remove(dequeuedThread);
				this.dequeuedThread.calcEffectivePriority();
				return this.dequeuedThread.thread;
			}
			else
				return null;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would
		 * return.
		 */
		protected ThreadState pickNextThread() {
			boolean intStatus = Machine.interrupt().disable();

			//ensure priorityQueue is properly ordered
			//does this take the old priorityQueue and reorder it? YES!!!
			//this.priorityQueue = new PriorityQueue<ThreadState>(priorityQueue);

			Machine.interrupt().restore(intStatus);
			return this.priorityQueue.peek();
		}
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * The base priority queue object.
		 */
		protected PriorityQueue<ThreadState> priorityQueue = new PriorityQueue<ThreadState>();
		/** The most recently dequeued ThreadState. */
		public ThreadState dequeuedThread;
		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;

		/**
		 * This method needs to be changed from its implementation in PriorityScheduler.
		 * This method should pick the next dequeued thread randomly from the tickets available
		 * in the waitList. For this to work the method must figure out the total number of tickets
		 * available in the queue and pick one randomly. Whichever ticket gets picked should be linked
		 * to a specific ThreadState. That ThreadState is then picked to acquire the resource, and
		 * the ThreadState that previously held the resource is removed. Each ThreadState that has 
		 * been changed must have its effectivePriority recalculated. 
		 * Note:Don't need pickNextThread() anymore.
		 * 
		 * @return KThread associated with a randomly chosen ticket
		 */


		protected Random generator = new Random();
	}
	protected class ThreadState implements Comparable<ThreadState>{
		/**
		 * We need to change this method from its implementation in PriorityScheduler because
		 * now instead of a max of all priorities, effectivePriority equals the sum of all the
		 * threads' priorities waiting on a resource. Since each ThreadState holds a LinkedList
		 * of all the ThreadQueues it currently holds the resource for, calculating the effective
		 * priority should be simple. Each thread's effectivePriority is calculated by the sum of
		 * its own priority and the effectivePriority of each thread that is waiting on the 
		 * resource held by this thread. Once the new value is calculated, calcEffectivePriority
		 * must be called on the thread that currently holds the resource for which this thread 
		 * is waiting on (if one exists). This will recursively compute the new effective priority
		 * for all threads that could be changed.
		 * @return total number of tickets this Thread has available to it.
		 */
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			//initialize the onQueue linkedlist
			this.onQueues = new LinkedList<PriorityThreadQueue>();
			this.age = Machine.timer().getTime();
			this.priority = priorityDefault; 
			//this.calcEffectivePriority();
			this.waiting = null;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}
		/**
		 * Calculate the Effective Priority of a thread and the thread that currently holds the resource
		 * it is waiting on.
		 */
		public void calcEffectivePriority() {
			int initialEffective = this.getEffectivePriority();
			int initialPriority = this.getPriority();
			int outsideEP = 0;
			if (this.onQueues.size() != 0){
				int size = this.onQueues.size();
				for(int i = 0; i < size; i++){
					PriorityThreadQueue current = onQueues.get(i);
					Iterator<ThreadState> threadIT = current.priorityQueue.iterator();
					while(threadIT.hasNext()){
						ThreadState currentThread = threadIT.next();
						outsideEP += currentThread.getEffectivePriority();
					}

				}
			}
			int totEP = initialPriority + outsideEP;
			this.effectivePriority = totEP;
			//now that my own effectivePriority Changes I have to recalculate the threads which i am waiting on
			if (this.waiting != null && this.waiting.dequeuedThread != null){
				//System.out.println(totEP - initialEffective);
				this.waiting.dequeuedThread.addToAllEffective(totEP - initialEffective);
			}
		}

		public int getEffectivePriority() {
			return this.effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			this.priority = priority;
			this.calcEffectivePriority();
		}

		public void addToAllEffective(int diff){
			/*
			boolean notDone = true;
			ThreadState current = this;
			LinkedList<ThreadState> seenSoFar = new LinkedList<ThreadState>();
			while (notDone) {
				//System.out.println("we be loopin");
				if (current.waiting != null && current.waiting.dequeuedThread != null){
					if (seenSoFar.indexOf(current) == -1){
						current.effectivePriority += diff;
						seenSoFar.add(current);
						current = current.waiting.dequeuedThread;
					}
					else{
						notDone = false;
					}
				}
				else{
					notDone = false;
				}
			}
		}
			 */
			this.effectivePriority += diff;
			if (this.waiting != null && this.waiting.dequeuedThread != null){
				this.waiting.dequeuedThread.addToAllEffective(diff);
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is
		 * now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityThreadQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			long time = Machine.timer().getTime();
			this.age = time;
			waitQueue.addToQueue(this);
			if (waitQueue.transferPriority){
				this.waiting = waitQueue;
			}
			this.calcEffectivePriority();
			if (waitQueue.dequeuedThread != null) {
				waitQueue.dequeuedThread.calcEffectivePriority();
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityThreadQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.priorityQueue.isEmpty());
			waitQueue.dequeuedThread = this;
			if (waitQueue.transferPriority) {
				this.addQueue(waitQueue);
			}
			this.calcEffectivePriority();
		}

		public int compareTo(ThreadState threadState){
			//changed first if from > to <
			if (threadState == null)
				return -1;
			if (this.getEffectivePriority() < threadState.getEffectivePriority()){
				return 1;
			}else{ if (this.getEffectivePriority() > threadState.getEffectivePriority()){
				return -1;
			}else{
				if (this.age >= threadState.age)
					return 1;
				else{ return -1; }
			}
			}
		}

		public void removeQueue(PriorityThreadQueue queue){
			onQueues.remove(queue);
			this.calcEffectivePriority();
		}
		public void addQueue(PriorityThreadQueue queue){
			onQueues.add(queue);
			this.calcEffectivePriority();
		}

		public String toString() {
			return "ThreadState thread=" + thread + ", priority=" + getPriority() + ", effective priority=" + getEffectivePriority();
		}
		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		/** The effective priority of the associated thread.
		 * Every time the effectivePriority is used it will be recalculated
		 * Might not need this actually!!!*/
		//protected int effectivePriority = calculateEffective();
		/** The age of the thread state relative to Nachos time. */
		public long age = Machine.timer().getTime();
		/** a linkedlist representing all the waitqueues it is getting priority from.*/
		protected LinkedList<PriorityThreadQueue> onQueues;
		protected int effectivePriority;
		protected PriorityThreadQueue waiting;
		protected boolean dirty;
	}
	public static void selfTest2() {
		System.out.println("---------LotteryScheduler test 1---------------------");
		LotteryScheduler s = new LotteryScheduler();
		ThreadQueue queue = s.newThreadQueue(true);
		ThreadQueue queue2 = s.newThreadQueue(true);
		ThreadQueue queue3 = s.newThreadQueue(true);
		
		KThread thread1 = new KThread();
		KThread thread2 = new KThread();
		KThread thread3 = new KThread();
		KThread thread4 = new KThread();
		thread1.setName("thread1");
		thread2.setName("thread2");
		thread3.setName("thread3");
		thread4.setName("thread4");

		
		boolean intStatus = Machine.interrupt().disable();
		
		queue3.acquire(thread1); // q3.owner = t1
		queue.acquire(thread1); // q1.owner = t1
		queue.waitForAccess(thread2); // q1.wait = t2
		queue2.acquire(thread4); // q2.owner = t4
		queue2.waitForAccess(thread1); // q2.wait = t1
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		s.getThreadState(thread2).setPriority(3); 
		// t2 donates 3 to t1,
		// t1 = 1 + 3 = 4
		// t1 donates  4 to t4,
		// t4 = 1 + 4 = 5
		
		System.out.println("After setting thread2's EP=3:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		queue.waitForAccess(thread3); // q1.wait = t2, t3
		s.getThreadState(thread3).setPriority(5); // t3 = 5
		// t3 donates 5 to t1
		// t1 = 1 + 3 + 5 = 9
		// t1 donates 9 to t4
		// t4 = 1 + 9 = 10
		
		System.out.println("After adding thread3 with EP=5:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		s.getThreadState(thread3).setPriority(2); // t3 = 2
		System.out.print("Queue 1 : ");
		queue.print();
		// t3 donates 2 to t1
		// t1 = 1 + 3 + 2 = 6
		// t1 donates 6 to t4
		// t4 = 1 + 6 = 7
		
		System.out.println("After setting thread3 EP=2:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		queue2.waitForAccess(thread4);
		// t3 donates 2 to t1
		// t1 = 1 + 3 + 2 = 6
		
		System.out.println("thread4 gives up queue2:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		System.out.println("--------End LotteryScheduler test 1------------------");
		System.out.println("\n--------LotteryScheduler test 2------------------");
		

		thread1 = new KThread();
		thread2 = new KThread();
		thread3 = new KThread();
		thread4 = new KThread();
		thread1.setName("thread1");
		thread2.setName("thread2");
		thread3.setName("thread3");
		thread4.setName("thread4");
		
		queue = s.newThreadQueue(false);

		queue.waitForAccess(thread2);
		queue.waitForAccess(thread3);
		queue.waitForAccess(thread4);

		queue.print();
		s.getThreadState(thread1).setPriority(1);
		queue.print();
		s.getThreadState(thread2).setPriority(1);
		queue.print();
		s.getThreadState(thread3).setPriority(49);
		queue.print();
		s.getThreadState(thread4).setPriority(49);
		queue.waitForAccess(thread1);
		
		queue.print();
		int tally[] = new int[4];
		for (int i = 0; i < 10000; ++i)
		{
			KThread n = queue.nextThread();
//			System.out.println("next returns " + n.getName());
			switch(n.getName())
			{
				case("thread1"):
					tally[0]++;
					break;
				case("thread2"):
					tally[1]++;
					break;
				case("thread3"):
					tally[2]++;
					break;
				case("thread4"):
					tally[3]++;
					break;
				default:
					System.out.println("ERROR: unknown thread name");
			}
//			queue.print();
			queue.waitForAccess(n);
//			queue.print();
		}
		System.out.print("Threads Run Result: ");
		for (int i = 0; i < tally.length; ++i)
		{
			System.out.print(tally[i] + "  ");
		}
		System.out.println("\n--------End LotteryScheduler test 2------------------");
		System.out.println("\n--------LotteryScheduler test 3------------------");
		
		thread1 = new KThread();
		thread2 = new KThread();
		thread3 = new KThread();
		thread4 = new KThread();
		thread1.setName("thread1");
		thread2.setName("thread2");
		thread3.setName("thread3");
		thread4.setName("thread4");
		
		queue = s.newThreadQueue(true);

		queue.acquire(thread1);
		queue.waitForAccess(thread2);
		s.getThreadState(thread2).setPriority(100);
		
		System.out.println("thread2 donates 99 tickets to thread1");
		System.out.println("thread1 EP = " + s.getThreadState(thread1).getEffectivePriority());
		queue.print();
		
		queue.waitForAccess(thread1);
		System.out.println("thread1 goes back to queue, returns 99 tickets");
		System.out.println("thread1 EP = " + s.getThreadState(thread1).getEffectivePriority());
		queue.print();
		
		queue.acquire(thread2);
		System.out.println("thread2 acquired queue, remains at 100 tickets");
		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
		queue.print();
		
		queue.waitForAccess(thread3);
		System.out.println("thread3 joins queue, donates nothing");
		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
		queue.print();
		
		s.getThreadState(thread3).setPriority(50);
		System.out.println("thread3 sets priority = 50, donates 49 to thread2");
		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
		queue.print();
		
		queue.waitForAccess(thread2);
		System.out.println("thread2 goes back to queue, returns 49 tickets");
		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
		queue.print();
		
		queue.acquire(thread3);
		System.out.println("thread3 acquired queue, gains 148 tickets");
		System.out.println("thread3 EP = " + s.getThreadState(thread3).getEffectivePriority());
		queue.print();
		
		s.getThreadState(thread3).setPriority(1);
		System.out.println("thread3 sets priority = 1, loses 49 tickets");
		System.out.println("thread3 EP = " + s.getThreadState(thread3).getEffectivePriority());
		queue.print();
		
		System.out.println("\n--------End LotteryScheduler test 3------------------");
		Machine.interrupt().restore(intStatus);
	}
}