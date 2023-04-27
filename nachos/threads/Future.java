package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */

    //when we know the function is finished, then we can make it work.

    //private boolean isFinished;
    private IntSupplier function;
    private Condition cv;
    private Lock lock;
    private int result;
    private boolean isCompleted;

    public Future (IntSupplier function) {
        lock = new Lock();
        cv = new Condition(lock);
        this.function = function;
        run();
    }
    private void run(){
            
            result = function.getAsInt();
            isCompleted = true;
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
    public int get () {
        //int value = function.getAsInt();
        lock.acquire();
        while (isCompleted == false){
            cv.sleep();
        }

        cv.wakeAll();
        lock.release();
	return function.getAsInt();
    }

    private static void FutureTest1(){

        IntSupplier function = () -> {
            // return a random integer between 0 and 100 (inclusive)
            return 1;
        };
        Future f = new Future(function);
        ThreadedKernel.alarm.waitUntil(100);
        System.out.println(f.get());
    }

    public static void selfTest(){
        FutureTest1();
    }
}