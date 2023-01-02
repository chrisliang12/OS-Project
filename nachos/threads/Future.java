package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    private KThread thread;
    private int value;
    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    public Future (IntSupplier function) {
        thread = new KThread( new Runnable () {
			public void run() {
				value = function.getAsInt();
			}
		});
        System.out.println("Time before running: " + " " + Machine.timer().getTime());
        thread.fork();
        System.out.println("thread starts running");

    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get() {
        System.out.println("Time calling get(): " + " " + Machine.timer().getTime());
	    thread.join();
        System.out.println(KThread.currentThread().getName() + " thread finish running" + " " + Machine.timer().getTime());
        return value; 
    }

    // test
    public static void test1() {
        System.out.println("======================================");
        System.out.println("Testing Future, #1");
        IntSupplier testFunc = new IntSupplier() {
            private int val = 0;

            public int getAsInt() {
                for (int i = 0; i < 1000000000; i++) {
                    val++;
                }
                return val;
            }
        };

        Future testFuture = new Future(testFunc);
        int res = testFuture.get();
        System.out.println("Time after running: " + Machine.timer().getTime() + " Result: " + res);
    }

    public static void test2() {
        System.out.println("======================================");
        System.out.println("Testing Future, #2");
        IntSupplier testFunc = new IntSupplier() {
            private int val = 0;

            public int getAsInt() {
                for (int i = 0; i < 1000000000; i++) {
                    val++;
                }
                return val;
            }
        };
        Future testFuture = new Future(testFunc);
        KThread t1 = new KThread( new Runnable () {
			public void run() {
                int res = testFuture.get();
                System.out.println("Time after running (t1): " + Machine.timer().getTime() + " Result: " + res);
			}
		});
        t1.setName("t1");

        
        t1.fork();
        int res = testFuture.get();
        System.out.println("Time after running: " + Machine.timer().getTime() + " Result: " + res);
        t1.join();
    }

    public static void test3() {
        System.out.println("======================================");
        System.out.println("Testing Future, #3");
        // call get() after the future is done, should return result immediately
        IntSupplier testFunc = new IntSupplier() {
            private int val = 0;

            public int getAsInt() {
                System.out.println("Time when getAsInt() is called: " + Machine.timer().getTime());
                for (int j = 0; j < 10; j++) {
                    for (int i = 0; i < 1000000000; i++) {
                        val += val % 2 == 0 ? val : -val;
                    }
                }
                System.out.println("Time when java-side complex computation is done: " + Machine.timer().getTime());
                ThreadedKernel.alarm.waitUntil(5000);

                System.out.println("Time right before getAsInt() return: " + Machine.timer().getTime());
                return val;
            }
        };
        Future testFuture = new Future(testFunc);
        ThreadedKernel.alarm.waitUntil(10000);
        System.out.println("Sleeped 10000 to reach time: " + Machine.timer().getTime());
        int res = testFuture.get();
        System.out.println("Time after running: " + Machine.timer().getTime() + " Result: " + res);
    }

    public static void selfTest() {
        test1();
        test2();
        test3();
        // for (int i = 0; i < 1000; i++) {
        //     System.out.println("---------------" + i + "----------------------");
        //     test2();
        // }
        
    }
}
