package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Initialize the new TreeSet and set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */
	public Alarm() {
		set = new TreeSet<Pair>();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Wakes up and 
	 * removes all threads that are ready to be woken up thread to yield, 
	 * forcing a context switch if there is another thread that should be run.
	 */
	public void timerInterrupt() {
		long currentTime = Machine.timer().getTime();
		boolean intStatus = Machine.interrupt().disable(); // calling ready() requires interrupts disabled

		// wake up and remove all threads in the TreeSet that needs to be woken up
		while (!set.isEmpty() && currentTime >= set.first().wakeTime) {
			Lib.debug(dbgAlarm, "Waking up thread " +  set.first().thread.toString() + " at " 
					+ currentTime + " cycles");
			set.first().thread.ready();
			set.pollFirst();
		}

		Machine.interrupt().restore(intStatus); // re-enable interrupts
		KThread.yield(); // yield the current thread
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks,
	 * waking it up in the timer interrupt handler. The thread must be
	 * woken up (placed in the scheduler ready set) during the first timer
	 * interrupt where
	 *
	 * <p><blockquote>
	 * (current time) >= (WaitUntil called time)+(x)
	 * </blockquote>
	 *
	 * @param	x	the minimum number of clock ticks to wait.
	 *
	 * @see	nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable(); // calling sleep() requires interrupts disabled

		// insert current thread to the set
		long wakeTime = Machine.timer().getTime() + x;
		Lib.debug(dbgAlarm, "At " + (wakeTime-x) + " cycles, sleeping thread " + KThread.currentThread().toString() + " until " + wakeTime + " cycles");
		set.add(new Pair(KThread.currentThread(), wakeTime));

		KThread.sleep(); // sleep the current thread
		Machine.interrupt().restore(intStatus); // re-enable interrupts
	}
	
	/**
	 * Tests whether this module is working. Call waitUntil on X KThreads 
	 * and see if they sleep and wake up at the right time
	 */
	public static void selfTest() {
		/**
		 * Runnable class to make KThread call waitUntil function
		 */
		class RunAlarm implements Runnable {
			public void run() {
				// Wake up thread after 500-1499 cycles
				ThreadedKernel.alarm.waitUntil((int)(Math.random() * 1000) + 500);
			}
		}
		
		Lib.debug(dbgAlarm, "Enter Alarm.selfTest");
		
		final int NUM_OF_THREADS = 7; // specify the desired number of threads to test with
		
		// set up KThreads for testing
		RunAlarm run = new RunAlarm();
		for(int i = 0; i < NUM_OF_THREADS; i++) {
			new KThread(run).setName("t"+i).fork();
		}
		
		KThread.yield(); // switch to t1 (will be switched to t2 next)
		
		// busy waiting to prevent this test from ending
		while (!ThreadedKernel.alarm.set.isEmpty()) {
			KThread.yield(); // keep switching threads (the main one and other available threads)
		}
		
		Lib.debug(dbgAlarm, "Exit Alarm.selfTest");
	}

	private static final char dbgAlarm = 'a';	// char for Lib.debug print tool

	/**
	 * Alarm's Data structure that manages sleeping threads
	 */
	private TreeSet<Pair> set; // used to store sleeping KThreads

	/**
	 * Element pair <KThread, long> that the TreeSet stores a pair of
	 * KThread and wakeTime
	 * <p><b>Note</b>: The instance variables are public. Do not abuse!
	 */
	private class Pair implements Comparable<Pair> {
		public KThread thread;
		public long wakeTime;
		public Pair(KThread theThread, long wakeTime) {
			this.thread = theThread;
			this.wakeTime = wakeTime;
		}

		/**
		 * Compare with the other pair 
		 * 
		 * @return negative if this.wakeTime < p.wakeTime
		 * 		   positive if this.wakeTime > p.wakeTime
		 * 		   zero if this.wakeTime == p.wakeTime
		 */
		public int compareTo(Pair p) {
			return Long.valueOf(this.wakeTime).compareTo(Long.valueOf(p.wakeTime));
		}
	}
}
