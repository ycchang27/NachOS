package nachos.threads;

import nachos.machine.*;
import java.util.TreeSet;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Element pair <KThread, long> that the TreeSet stores
	 */
	public class Pair implements Comparable<Pair> {
		public KThread thread;
		public long wakeTime;
		public Pair(KThread theThread, long wakeTime) {
			this.thread = theThread;
			this.wakeTime = wakeTime;
		}
		/**
		 * Compare with other thread 
		 * 
		 * @return negative if this < kt
		 * 		   positive if this > kt
		 * 		   zero if this == kt
		 */
		public int compareTo(Pair p) {
	        return (int)(this.wakeTime - p.wakeTime);
	    }
	}
	private TreeSet<Pair> set; // A waiting queue to pop when the current time passes a certain time
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
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
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread
	 * that should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable(); // calling sleep() requires interrupts disabled
		while (!set.isEmpty() && Machine.timer().getTime() >= set.first().wakeTime) {
			set.first().thread.ready();
			set.pollFirst();
		}
		Machine.interrupt().restore(intStatus); // re-enable interrupts
		KThread.currentThread().yield();
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
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x; // save to current thread's local variable
		Pair p = new Pair(KThread.currentThread(), wakeTime); // pair
		KThread.currentThread().sleep();
		set.add(p); // add to the TreeSet
//		while (KThread.currentThread().wakeTime > Machine.timer().getTime()) // check if current thread should wake
//			KThread.currentThread().yield();	// else sleep
	}
}
