package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    private Lock lock;
    private Map<Integer, Condition> tagToCV;
    private Map<Integer, Integer> tagToValue;
    public Rendezvous () {
        lock = new Lock();
        tagToCV = new HashMap<Integer, Condition>();
        tagToValue = new HashMap<Integer, Integer>();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        lock.acquire();
        if (!tagToCV.containsKey(tag)){
            System.out.println(KThread.currentThread().getName() + tag + " " + value);
            Condition cv = new Condition(lock);
            tagToCV.put(tag, cv);
            tagToValue.put(tag, value);
            cv.sleep();
            System.out.println("Ok, I wake up.");
            int exchangeValue = tagToValue.get(tag);
            tagToCV.remove(tag);
            tagToValue.remove(tag);
            lock.release();
            return exchangeValue;
        }
        else {
            System.out.println(KThread.currentThread().getName() + tag + " " + value);
            Condition cv = tagToCV.get(tag);
            int exchangevalue = tagToValue.get(tag);
            tagToValue.put(tag, value);
            cv.wake();     
            lock.release();       
            return exchangevalue;
        }
    }

    public static void rendezTest1() {
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = -1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
            System.out.println("Can not work here.");
		    int recv = r.exchange (tag, send);
            System.out.println(recv);
		    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t1.setName("t1");
	KThread t2 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = 1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t2.setName("t2");

	t1.fork(); t2.fork();
	// assumes join is implemented correctly
	t1.join(); t2.join();
    }

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
	// place calls to your Rendezvous tests that you implement here
	rendezTest1();
    }

}
