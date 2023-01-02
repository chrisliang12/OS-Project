package nachos.threads;

import nachos.machine.*;

import java.util.HashMap;
import java.util.Map;

class RendezvousContainer {
    private Lock lock;
    private Lock map_lock;
    private Condition condition;
    private KThread thread;
    private int value;

    public RendezvousContainer(KThread thread, int value, Lock map_lock) {
        this.thread = thread;
        this.value = value;
        this.map_lock = map_lock;
        lock = new Lock();
        condition = new Condition(lock);
        System.out.println("RendezvousContainer: " + thread.getName() + " " + value);
    }

    public int arrive(int value) {
        lock.acquire();
        map_lock.release();
        Integer oldValue = this.value;
        this.value = value;
        condition.wake();
        lock.release();
        System.out.println("arrive: " + thread.getName() + " returning " + value);
        return oldValue;
    }

    public int depart() {
        lock.acquire();
        map_lock.release();
        condition.sleep();
        lock.release();
        System.out.println("depart: " + thread.getName() + " returning " + value);
        return value;
    }
}

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    Map<Integer, RendezvousContainer> map;
    Lock lock;

    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous() {
        map = new HashMap<Integer, RendezvousContainer>();
        lock = new Lock();
    }

    /**
     * Synchronously exchange a value with another thread. The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y). When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     * <p>
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other). The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag   the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange(int tag, int value) {
        String thread_name = KThread.currentThread().getName();
        System.out.println("exchange: " + thread_name + " " + tag + " " + value + " called");
        lock.acquire();
        RendezvousContainer container = map.get(tag);
        if (container == null) {
            container = new RendezvousContainer(KThread.currentThread(), value, lock);
            map.put(tag, container);
            return container.depart();
        } else {
            map.remove(tag);
            return container.arrive(value);
        }
    }

    // Place Rendezvous test code inside of the Rendezvous class.

    public static Runnable createExchangeRunnable(final int tag, final int value, final int expected, final Rendezvous rendezvous) {
        return new Runnable() {
            public void run() {
                System.out.println("ExchangeRunnable: " + KThread.currentThread().getName() + " " + tag + " " + value + " " + expected + " called");
                int result = rendezvous.exchange(tag, value);
                Lib.assertTrue(result == expected, "result = " + result + ", expected = " + expected);
                System.out.println("ExchangeRunnable: " + KThread.currentThread().getName() + " " + tag + " " + value + " " + expected + " finished");
            }
        };
    }

    public static void rendezTest1() {
        System.out.println("======================================");
        System.out.println("Testing Rendezvous, #1");
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(createExchangeRunnable(0, -1, 1, r));
        t1.setName("t1");
        KThread t2 = new KThread(createExchangeRunnable(0, 1, -1, r));
        t2.setName("t2");

        t1.fork();
        t2.fork();
        // assumes join is implemented correctly
        t1.join();
        t2.join();
    }

    public static void rendezTest2() {
        // Test 2: Two Threads, Multiple rendezvous with the same tag
        System.out.println("======================================");
        System.out.println("Testing Rendezvous, #2");
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);

                send = -2;
                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                recv = r.exchange(tag, send);
                Lib.assertTrue(recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange(tag, send);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);

                send = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                recv = r.exchange(tag, send);
                Lib.assertTrue(recv == -2, "Was expecting " + -2 + " but received " + recv);
                System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

        t1.fork();
        t2.fork();
        // assumes join is implemented correctly
        t1.join();
        t2.join();
    }

    public static void rendezTest3() {
        // Test 3: Multiple Threads, one rendezvous with the same tag
        System.out.println("======================================");
        System.out.println("Testing Rendezvous, #3");
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(createExchangeRunnable(0, -1, 1, r));
        t1.setName("t1");
        KThread t2 = new KThread(createExchangeRunnable(0, 1, -1, r));
        t2.setName("t2");
        KThread t3 = new KThread(createExchangeRunnable(0, -2, 2, r));
        t3.setName("t3");
        KThread t4 = new KThread(createExchangeRunnable(0, 2, -2, r));
        t4.setName("t4");
        KThread t5 = new KThread(createExchangeRunnable(0, -3, 3, r));
        t5.setName("t5");
        KThread t6 = new KThread(createExchangeRunnable(0, 3, -3, r));
        t6.setName("t6");

        t1.fork();
        t2.fork();
        t1.join();
        t2.join();

        t3.fork();
        t4.fork();
        t3.join();
        t4.join();

        t5.fork();
        t6.fork();
        t5.join();
        t6.join();
    }

    public static void rendezTest4() {
        // Test 4: Multiple Threads, multiple rendezvous with different tags
        System.out.println("======================================");
        System.out.println("Testing Rendezvous, #4");
        final Rendezvous r1 = new Rendezvous();
        final Rendezvous r2 = new Rendezvous();

        KThread t1 = new KThread(createExchangeRunnable(0, -1, 1, r1));
        t1.setName("t1");
        KThread t2 = new KThread(createExchangeRunnable(0, 1, -1, r1));
        t2.setName("t2");
        KThread t3 = new KThread(createExchangeRunnable(1, -2, 2, r2));
        t3.setName("t3");
        KThread t4 = new KThread(createExchangeRunnable(1, 2, -2, r2));
        t4.setName("t4");
        KThread t5 = new KThread(createExchangeRunnable(2, -3, 3, r1));
        t5.setName("t5");
        KThread t6 = new KThread(createExchangeRunnable(2, 3, -3, r1));
        t6.setName("t6");
        KThread t7 = new KThread(createExchangeRunnable(3, -4, 4, r2));
        t7.setName("t7");
        KThread t8 = new KThread(createExchangeRunnable(3, 4, -4, r2));
        t8.setName("t8");

        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();
        System.out.println("1234 forked");
        t1.join();
        System.out.println("t1 joined");
        t2.join();
        System.out.println("t2 joined");
        t3.join();
        System.out.println("t3 joined");
        t4.join();
        System.out.println("t4 joined");

        System.out.println("1234 done");

        t5.fork();
        t6.fork();
        t7.fork();
        t8.fork();
        t5.join();
        t6.join();
        t7.join();
        t8.join();
    }

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        rendezTest2();
        // for (int i=0; i<100; i++) {
        //     rendezTest4();
        //     System.out.println("Test #" + i + " passed");
        // }
        // rendezTest2();
        rendezTest3();
        rendezTest4();
    }
}
