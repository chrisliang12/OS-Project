package nachos.threads;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.management.loading.MLetContent;

import nachos.machine.*;

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
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});

		// initialize Wait List
		this.waitList = new LinkedList<WaitThread>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		Machine.interrupt().disable();
		Iterator<WaitThread> itr = waitList.iterator();
		while (itr.hasNext()) {
			WaitThread thread = itr.next();
			if (thread.wakeTime <= Machine.timer().getTime()) {
				thread.currThread.ready();
				itr.remove();
			}
		}
		KThread.yield();
		Machine.interrupt().enable();
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
		if (x <= 0)
			return;
		boolean intStatus = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		WaitThread currWaitThread = new WaitThread(KThread.currentThread(), wakeTime);

		waitList.add(currWaitThread);
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	public void printList() {
		for (WaitThread thr : waitList) {
			System.out.println("Alarm Class: " + thr.currThread.getName());
		}
	}

	private class WaitThread {
		public KThread currThread;
		public long wakeTime;

		public WaitThread(KThread thread, long x) {
			this.currThread = thread;
			this.wakeTime = x;
		}
	}

	// Priority Queue storing threads which are currently waiting
	private LinkedList<WaitThread> waitList;

	// Alarm testing code
	public static void alarmTest1() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// implement more test methods here
	public static void alarmTest2() {
		int durations[] = { 50, 50, 50 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest2: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();

		// Invoke other test methods here
		alarmTest2();
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();
		Iterator<WaitThread> itr = waitList.iterator();
		while (itr.hasNext()) {
			WaitThread curr = itr.next();
			if (curr.currThread == thread) {
				thread.ready();
				itr.remove();
				Machine.interrupt().restore(intStatus);
				return true;
			}
		}
		Machine.interrupt().restore(intStatus);
		return false;
	}
}
