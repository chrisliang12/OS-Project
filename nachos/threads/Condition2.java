package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.threadQueue = new LinkedList<KThread>();
		this.timeoutQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		Machine.interrupt().disable();
		conditionLock.release();

		KThread currentThread = KThread.currentThread();
		//add the current thread to the end of the queue and 
		// make it sleep in order to wake it up in the future
		threadQueue.addLast(currentThread);
		currentThread.sleep();

		conditionLock.acquire();
		Machine.interrupt().enable();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		Machine.interrupt().disable();
		
		// make the first thread in the queue ready to run
		if (!threadQueue.isEmpty()) {
			threadQueue.removeFirst().ready();
			// boolean cancelSuccess = ThreadedKernel.alarm.cancel(threadQueue.getFirst());
			// if (cancelSuccess)
			// 	System.out.println("cancelSuccess: --------------------------------- ");
 			// if (!cancelSuccess) threadQueue.removeFirst().ready();
		} else if (!timeoutQueue.isEmpty()) {
			boolean cancelSuccess = false;
			while (!cancelSuccess && !timeoutQueue.isEmpty()) {
				cancelSuccess = ThreadedKernel.alarm.cancel(timeoutQueue.getFirst());
				timeoutQueue.removeFirst();
			}
		}
		Machine.interrupt().enable();
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		Machine.interrupt().disable();

		// make all the threads in the queue ready to run
		while (!threadQueue.isEmpty()) {
			threadQueue.removeFirst().ready();
		}

		while (!timeoutQueue.isEmpty()) {
			boolean cancelSuccess = ThreadedKernel.alarm.cancel(timeoutQueue.getFirst());
			timeoutQueue.removeFirst();
		}
		Machine.interrupt().enable();
	}

	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		conditionLock.release();
		timeoutQueue.addLast(KThread.currentThread());
		ThreadedKernel.alarm.waitUntil(timeout);
		//ThreadedKernel.alarm.printList();
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	public void printList() {
		for (KThread thr : threadQueue) {
			System.out.println("CV Class: " + thr.getName());
		}
		for (KThread thr: timeoutQueue) {
			System.out.println("CV Class: " + thr.getName());
		}
	}

	private Lock conditionLock;
  	private LinkedList<KThread> threadQueue;
	private LinkedList<KThread> timeoutQueue;


	private static class InterLockTest {
		private static Lock lock;
		private static Condition2 cv;

		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();
					cv.sleep();
				}
				lock.release();
			}
		}

		public InterLockTest() {
			System.out.println("======================================");
        	System.out.println("Testing CV2, #1");
			lock = new Lock();
			cv = new Condition2(lock);

			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();
			for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
			// ping.join();

		}

	}
	    // Place Condition2 test code inside of the Condition2 class.

    // Test programs should have exactly the same behavior with the
    // Condition and Condition2 classes.  You can first try a test with
    // Condition, which is already provided for you, and then try it
    // with Condition2, which you are implementing, and compare their
    // behavior.

    // Do not use this test program as your first Condition2 test.
    // First test it with more basic test programs to verify specific
    // functionality.

    public static void cvTest5() {
		System.out.println("======================================");
        System.out.println("Testing CV2, #2");
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

	private static void sleepForTest1() {
		System.out.println("======================================");
        System.out.println("Testing CV2, #3");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up, slept for " + (t1 - t0) + " ticks");
		Lib.assertTrue(t1 - t0 >= 2000, "Thread slept for too short a time");
		lock.release();
	}	

	private static void sleepForTest2() {
		System.out.println("======================================");
        System.out.println("Testing CV2, #4");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		final LinkedList<Integer> testCondition = new LinkedList<>();
		
		KThread thread0 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(500);
				cv.printList();
				cv.wakeAll();
				long t1 = Machine.timer().getTime();
				System.out.println (KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
				Lib.assertTrue(t1 - t0 >= 500, "Thread slept for too short a time");
				testCondition.add(1);
				lock.release();
			}
		});

		KThread thread1 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println (KThread.currentThread().getName() + " sleeping");
				while (testCondition.isEmpty()) {
					cv.sleepFor(2000);
					long t1 = Machine.timer().getTime();
					System.out.println (KThread.currentThread().getName() +
							" woke up, slept for " + (t1 - t0) + " ticks");
					Lib.assertTrue(t1 - t0 >=500, "Thread slept for too short a time");
					Lib.assertTrue(t1 - t0 < 2000, "Thread slept for too long a time");
				}
				lock.release();
			}
		});

		thread0.setName("thread 0");
		thread1.setName("thread 1");
		thread1.fork();
		thread0.fork();
		

		thread0.join();
		thread1.join();

		// long t0 = Machine.timer().getTime();
		// System.out.println (KThread.currentThread().getName() + " sleeping");
		// // no other thread will wake us up, so we should time out
		// cv.sleepFor(2000);

		// long t1 = Machine.timer().getTime();
		// System.out.println (KThread.currentThread().getName() +
		// 			" woke up, slept for " + (t1 - t0) + " ticks");
		// lock.release();
	}	


	public static void selfTest() {
		cvTest5();
		new InterLockTest();
		sleepForTest1();
		sleepForTest2();
	}
}
