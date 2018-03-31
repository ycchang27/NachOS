package nachos.threads;

import nachos.machine.*;

import java.util.Random;
import java.util.HashSet;

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
public class LotteryScheduler extends PriorityScheduler {
    public LotteryScheduler() {
    }
    
    @Override
    public ThreadQueue newThreadQueue(boolean transferPriority){
    	return new LotteryQueue(transferPriority);
    }
    
    protected class ThreadState extends PriorityScheduler.ThreadState{
		public KThread thread;
		protected int priority = 0;
		public int ePriority = 0;
		public int donated = 0;
    	public int base = 0;		// used by LotteryQueue.nextThread()
    	public ThreadState borrower;	// keep a reference to thread donated to
    	public HashSet<LotteryQueue> acquired;	// set of acquired queues
    	public LotteryQueue waiting; 	// reference to queue that this is waiting in
    	
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.priority = this.ePriority = priorityDefault;
			acquired = new HashSet<LotteryQueue>();
		}

		// calculates the effective priority of this thread -> ePriority
		public void calcEffective(boolean dirty){
			if (dirty) reset();		// if dirty, reset for correctness
			for (LotteryQueue Q : acquired){ // handle donations from all acquired queue
				Q.update();
			}
			if (waiting != null) waiting.update(); // handle donation in queue this is waiting in
		}
		
		// donation helper function that adds this thread's tickets to T's tickets
		public void donateTo(ThreadState T){
			revert(); // reclaim previous donation
			if (this.ePriority > 0)
			{
				donated = this.ePriority; // save amount donated
				T.ePriority += donated; // donate 
				borrower = T; // save a reference to thread donated to
			}
		}
		
		// helper function to reclaim donated amount from borrower
		public void revert(){
			if (borrower != null){ // recursion condition
				borrower.revert(); // borrower should also reclaim donations
				borrower.ePriority -= donated; // reclaim donation
				donated = 0;
				borrower = null; // remove reference to borrower
			}
		}

		// helper function to return this thread to a state before donating/accepting donations
	    @Override
		public void reset(){
			revert(); // this thread reverts
			for (LotteryQueue Q : acquired){ // all threads in acquired queues revert
				for (ThreadState T : Q.waitQueue){
					T.revert();
				}
			}
			this.base = 0;
		}

		public int getPriority() {return priority;}

	    @Override
		public int getEffectivePriority() {
			overkill(); // calculate this thread's effective priority just before returning it
			return (ePriority > 0)? ePriority:1;
		}

	    @Override
		public void setPriority(int priority) {
			if (priority > 0 && priority != this.priority)
			{
				reset(); // reset this thread before modifying it
				this.priority = this.ePriority = priority;
				calcEffective(false); // calculate effective priority
			}
		}

		public void waitForAccess(LotteryQueue waitQueue) {
			reset(); // reset this thread before modifying it
			if (acquired.remove(waitQueue)){ // if previously owned waitQueue, mark not acquired
				waitQueue.owner = null;
			}
			waiting = waitQueue; // save reference to waitQueue
			waiting.waitQueue.add(this); // add this to waitQueue
			calcEffective(false); // calculate effective priority
		}

		public void acquire(LotteryQueue acquiredQueue) {
			reset(); // reset this thread before modifying it
			if (waiting == acquiredQueue){ // if previously waiting on queue, mark not waiting
				waiting.waitQueue.remove(this);
				waiting = null;
			}
			if (acquiredQueue.owner != null && acquiredQueue.owner != this)
			{
				ThreadState T = acquiredQueue.owner;
				T.reset(); // reset previous owner
				T.acquired.remove(acquiredQueue); // previous owner no longer references queue
				T.calcEffective(true); // previous owner calculates its effective priority
			}
			acquiredQueue.owner = this; // this thread becomes owner of queue
			calcEffective(!acquired.add(acquiredQueue)); // calculate effective priority
		}

		// helper function that calculates the correct effective priority of this thread
		// this function may have overlapping behavior with other methods
		// called before returning effective priority in getEffectivePriority()
		public void overkill(){
			for (LotteryQueue Q : acquired){ // recursively call overkill on ALL threads waiting on this
				for (ThreadState T : Q.waitQueue){
					T.overkill();
				}
				Q.update(); // claim donations from acquired queues
			}
			revert(); // reclaim donation
			if (waiting != null) waiting.update(); // donate if possible, then ePriority will be correct
		}
	}

    @Override
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}
    
    protected class LotteryQueue extends PriorityQueue{
    	ThreadState owner;
    	HashSet<ThreadState> waitQueue;
    	Random rd; // Java Random class
    	int numTickets = 0; // number of tickets in this queue
    	
    	LotteryQueue(boolean transferPriority){
    		this.transferPriority = transferPriority;
    		waitQueue = new HashSet<ThreadState>();
    		rd = new Random();
    	}
    	
    	// handle donations for lottery
    	public void update(){
    		numTickets = 0; // number of tickets starts at 0
    		for (ThreadState T : waitQueue){ // all waiting threads donate then submit tickets
    			if (owner != null && this.transferPriority) T.donateTo(owner);
    			T.base = numTickets; // thread T owns all tickets numbered in range [base, base + T.getEffective...)
    			numTickets += T.ePriority;
    		}
    	}

		@Override
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		@Override
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (numTickets == 0) return null; // no threads are waiting
			int winner = rd.nextInt(numTickets); // nextInt returns random number in [0, arg0)
			for (ThreadState T : waitQueue){ // search queue for next thread to run
				if (winner >= T.base && winner < T.base + T.getEffectivePriority()){ 
					T.acquire(this);
					return T.thread;
				}
			}
			return null;
		}

		@Override
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		@Override
		public void print() {
			if (owner != null) System.out.print(owner.thread.getName() + "; ");
			for (ThreadState T : waitQueue){
				System.out.print(T.thread.getName() + "[" + T.base + ", " + (T.base + T.getEffectivePriority()) + "), ");
			}
			System.out.println();
		}
    }

    @Override
	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

    @Override
	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

    @Override
	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= 1 &&
				priority <= Integer.MAX_VALUE);

		getThreadState(thread).setPriority(priority);
	}

    @Override
    public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == Integer.MAX_VALUE)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

    @Override
	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == 1)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}
    
//    public static void selfTest() {
//		System.out.println("---------LotteryScheduler test 1---------------------");
//		LotteryScheduler s = new LotteryScheduler();
//		ThreadQueue queue = s.newThreadQueue(true);
//		ThreadQueue queue2 = s.newThreadQueue(true);
//		ThreadQueue queue3 = s.newThreadQueue(true);
//		
//		KThread thread1 = new KThread();
//		KThread thread2 = new KThread();
//		KThread thread3 = new KThread();
//		KThread thread4 = new KThread();
//		thread1.setName("thread1");
//		thread2.setName("thread2");
//		thread3.setName("thread3");
//		thread4.setName("thread4");
//
//		
//		boolean intStatus = Machine.interrupt().disable();
//		
//		queue3.acquire(thread1); // q3.owner = t1
//		queue.acquire(thread1); // q1.owner = t1
//		queue.waitForAccess(thread2); // q1.wait = t2
//		queue2.acquire(thread4); // q2.owner = t4
//		queue2.waitForAccess(thread1); // q2.wait = t1
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		s.getThreadState(thread2).setPriority(3); 
//		System.out.println("After setting thread2's EP=3:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		queue.waitForAccess(thread3);
//		s.getThreadState(thread3).setPriority(5);
//		
//		System.out.println("After adding thread3 with EP=5:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		s.getThreadState(thread3).setPriority(2);
//		System.out.print("Queue 1 : ");
//		queue.print();
//		
//		System.out.println("After setting thread3 EP=2:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		queue2.waitForAccess(thread4);
//		
//		System.out.println("thread4 gives up queue2:");
//		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
//		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
//		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
//		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
//		
//		System.out.println("--------End LotteryScheduler test 1------------------");
//		System.out.println("\n--------LotteryScheduler test 2------------------");
//		
//
//		thread1 = new KThread();
//		thread2 = new KThread();
//		thread3 = new KThread();
//		thread4 = new KThread();
//		thread1.setName("thread1");
//		thread2.setName("thread2");
//		thread3.setName("thread3");
//		thread4.setName("thread4");
//		
//		queue = s.newThreadQueue(false);
//
//		queue.waitForAccess(thread2);
//		queue.waitForAccess(thread3);
//		queue.waitForAccess(thread4);
//
//		queue.print();
//		s.getThreadState(thread1).setPriority(1);
//		queue.print();
//		s.getThreadState(thread2).setPriority(1);
//		queue.print();
//		s.getThreadState(thread3).setPriority(49);
//		queue.print();
//		s.getThreadState(thread4).setPriority(49);
//		queue.waitForAccess(thread1);
//		
//		queue.print();
//		int tally[] = new int[4];
//		for (int i = 0; i < 10000; ++i)
//		{
//			KThread n = queue.nextThread();
//			switch(n.getName())
//			{
//				case("thread1"):
//					tally[0]++;
//					break;
//				case("thread2"):
//					tally[1]++;
//					break;
//				case("thread3"):
//					tally[2]++;
//					break;
//				case("thread4"):
//					tally[3]++;
//					break;
//				default:
//					System.out.println("ERROR: unknown thread name");
//			}
//			queue.waitForAccess(n);
//		}
//		System.out.print("Threads Run Result: ");
//		for (int i = 0; i < tally.length; ++i)
//		{
//			System.out.print(tally[i] + "  ");
//		}
//		System.out.println("\n--------End LotteryScheduler test 2------------------");
//		System.out.println("\n--------LotteryScheduler test 3------------------");
//		
//		thread1 = new KThread();
//		thread2 = new KThread();
//		thread3 = new KThread();
//		thread4 = new KThread();
//		thread1.setName("thread1");
//		thread2.setName("thread2");
//		thread3.setName("thread3");
//		thread4.setName("thread4");
//		
//		queue = s.newThreadQueue(true);
//
//		queue.acquire(thread1);
//		queue.waitForAccess(thread2);
//		s.getThreadState(thread2).setPriority(100);
//		
//		System.out.println("thread2 donates 99 tickets to thread1");
//		System.out.println("thread1 EP = " + s.getThreadState(thread1).getEffectivePriority());
//		queue.print();
//		
//		queue.waitForAccess(thread1);
//		System.out.println("thread1 goes back to queue, returns 99 tickets");
//		System.out.println("thread1 EP = " + s.getThreadState(thread1).getEffectivePriority());
//		queue.print();
//		
//		queue.acquire(thread2);
//		System.out.println("thread2 acquired queue, remains at 100 tickets");
//		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
//		queue.print();
//		
//		queue.waitForAccess(thread3);
//		System.out.println("thread3 joins queue, donates nothing");
//		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
//		queue.print();
//		
//		s.getThreadState(thread3).setPriority(50);
//		System.out.println("thread3 sets priority = 50, donates 49 to thread2");
//		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
//		queue.print();
//		
//		queue.waitForAccess(thread2);
//		System.out.println("thread2 goes back to queue, returns 49 tickets");
//		System.out.println("thread2 EP = " + s.getThreadState(thread2).getEffectivePriority());
//		queue.print();
//		
//		queue.acquire(thread3);
//		System.out.println("thread3 acquired queue, gains 148 tickets");
//		System.out.println("thread3 EP = " + s.getThreadState(thread3).getEffectivePriority());
//		queue.print();
//		
//		s.getThreadState(thread3).setPriority(1);
//		System.out.println("thread3 sets priority = 1, loses 49 tickets");
//		System.out.println("thread3 EP = " + s.getThreadState(thread3).getEffectivePriority());
//		queue.print();
//		
//		System.out.println("\n--------End LotteryScheduler test 3------------------");
//		Machine.interrupt().restore(intStatus);
//	}
}
