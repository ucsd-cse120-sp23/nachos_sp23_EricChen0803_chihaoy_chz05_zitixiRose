package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */

	//initialize the priority queue
	PriorityQueue<Pair> pq = new PriorityQueue<Pair>();

	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		while (!pq.isEmpty() && pq.peek().getValue() <= Machine.timer().getTime()){
			//Machine.interrupt().disable();
			pq.peek().getKey().ready();
			//Machine.interrupt().enable();
			pq.poll();
		}
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		Machine.interrupt().disable();
		if (x > 0){
			
			long wakeTime = Machine.timer().getTime() + x;
			pq.add(new Pair(KThread.currentThread(), wakeTime));
			while (wakeTime > Machine.timer().getTime()){
				KThread.currentThread().sleep();
			}				
			
		}
		Machine.interrupt().enable();
		return;
		
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
			for (Pair p : pq){
				if (p.getKey().equals(thread)){
					pq.remove(p);
					return true;
				}
			}
		return false;
	}

	class Pair implements Comparable<Pair>{
		private KThread key;
		private long value;
		Pair(KThread key, long value){
			this.key = key;
		    this.value = value;
		}
		public int compareTo(Pair o2) {
			if (this.value < o2.value)
				return -1;
			else if (this.value==o2.value)
				return 0;
			else
				return 1;
		}
		public KThread getKey(){
			return key;
		}
		public long getValue(){
			return value;
		}
	}
}
