package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.Random;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

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
    	public int base = 0;
    	public HashSet<LotteryQueue> acquired;
    	public LotteryQueue waiting; 
    	
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.priority = this.ePriority = priorityDefault;
			acquired = new HashSet<LotteryQueue>();
		}
		
		public void calcEffective(){
//			System.out.println(thread.getName() + " calculates effective");
			reset();
			for (LotteryQueue Q : acquired){
				Q.update();
			}
			if (waiting != null){
				if (waiting.owner != null) waiting.owner.calcEffective();
				else waiting.update();
			}
		}
		
		public void donateTo(ThreadState T){
			if (this.ePriority > 1)
			{
//				System.out.println(thread.getName() + " donates " + (this.ePriority-1) + " to " + T.thread.getName());
				donated = this.ePriority - 1;
				this.ePriority = 1;
				T.ePriority += donated;
			}
		}
		
		public void revert(ThreadState T){
			if (donated != 0){
//				System.out.println(T.thread.getName() + " returns " + donated);
				T.ePriority -= donated;
				if (T.ePriority < 1){
					T.revert(T.waiting.owner);
				}
				this.ePriority += donated;
				donated = 0;
			}
		}
		
		public void reset(){
			if (donated != 0){
				revert(waiting.owner);
			}
			if (this.ePriority != this.priority)
			{
				for (LotteryQueue Q : acquired){
					for (ThreadState T : Q.waitQueue){
						T.revert(this);
					}
				}
			}
			this.base = 0;
		}

		public int getPriority() {return priority;}

		public int getEffectivePriority() {return ePriority;}

		public void setPriority(int priority) {
			if (priority > 0 && priority != this.priority)
			{
				reset();
				this.priority = this.ePriority = priority;
				calcEffective();
			}
		}

		public void waitForAccess(LotteryQueue waitQueue) {
			if (acquired.remove(waitQueue)){
				waitQueue.owner = null;
			}
			waiting = waitQueue;
			waiting.waitQueue.add(this);
			if (waiting.owner != null) waiting.owner.calcEffective();
			else waiting.update();
		}

		public void acquire(LotteryQueue acquiredQueue) {
			if (waiting == acquiredQueue){
				waiting.waitQueue.remove(this);
				waiting = null;
			}
			acquired.add(acquiredQueue);
			if (acquiredQueue.owner != null)
			{
				ThreadState T = acquiredQueue.owner;
				T.acquired.remove(acquiredQueue);
				T.calcEffective();
			}
			acquiredQueue.owner = this;
			calcEffective();
		}
	}

	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}
    
    protected class LotteryQueue extends PriorityQueue{
    	ThreadState owner;
    	HashSet<ThreadState> waitQueue;
    	Random rd;
    	int numTickets = 0;
    	
    	LotteryQueue(boolean transferPriority){
    		this.transferPriority = transferPriority;
    		waitQueue = new HashSet<ThreadState>();
    		rd = new Random();
    	}
    	
    	// handle donations for lottery
    	public void update(){
    		numTickets = 0;
    		for (ThreadState T : waitQueue){
    			if (owner != null && this.transferPriority) T.donateTo(owner);
    			T.base = numTickets;
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
			if (numTickets == 0) return null;
			int winner = rd.nextInt(numTickets); // nextInt returns [0, arg0)
//			System.out.println("Winning number = " + winner + " out of # tickets = " + numTickets);
			for (ThreadState T : waitQueue){
				if (winner >= T.base && winner < T.base + T.ePriority){
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
				System.out.print(T.thread.getName() + "[" + T.base + ", " + (T.base + T.ePriority) + "), ");
			}
			System.out.println();
		}
    }
    
    public static void selfTest() {
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
		// t2 donates to t1,
		// t1 = 1 + 2 = 3
		// t1 donates to t4,
		// t4 = 1 + 2 = 3
		
		System.out.println("After setting thread2's EP=3:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		queue.waitForAccess(thread3); // q1.wait = t2, t3
		s.getThreadState(thread3).setPriority(5); // t3 = 5
		// t3 donates to t1
		// t1 = 1 + 2 + 4 = 7
		// t1 donates to t4
		// t4 = 1 + 6 = 7
		
		System.out.println("After adding thread3 with EP=5:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		s.getThreadState(thread3).setPriority(2); // t3 = 2
		System.out.print("Queue 1 : ");
		queue.print();
		// t3 donates to t1
		// t1 = 1 + 2 + 1 = 4
		// t1 donates to t4
		// t4 = 1 + 3 = 4
		
		System.out.println("After setting thread3 EP=2:");
		System.out.println("thread1 EP="+s.getThreadState(thread1).getEffectivePriority());
		System.out.println("thread2 EP="+s.getThreadState(thread2).getEffectivePriority());
		System.out.println("thread3 EP="+s.getThreadState(thread3).getEffectivePriority());
		System.out.println("thread4 EP="+s.getThreadState(thread4).getEffectivePriority());
		
		s.getThreadState(thread3).setPriority(1);
		s.getThreadState(thread2).setPriority(1);
		
		System.out.println("After setting all priorities to 1:");
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

		queue.waitForAccess(thread1);
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
		
		queue.print();
		int tally[] = new int[4];
		for (int i = 0; i < 1000; ++i)
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
		}
		System.out.print("Threads Run Result: ");
		for (int i = 0; i < tally.length; ++i)
		{
			System.out.print(tally[i] + "  ");
		}
		System.out.println("\n--------End LotteryScheduler test 2------------------");
		Machine.interrupt().restore(intStatus);
	}
}
