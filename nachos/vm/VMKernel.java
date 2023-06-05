package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		int numPhysPages = Machine.processor().getNumPhysPages();
		super.initialize(args);
		vmLock = new Lock();
		//TODO: initialize the IPT
		victimPage = 0;
		IPT = new HashMap <Integer, VMProcess>();
		for (int ppn = 0; ppn < numPhysPages; ppn++){
			IPT.put(ppn, null);
		}
		IPV = new HashMap <Integer, Integer>();
		for (int ppn = 0; ppn < numPhysPages; ppn++){
			IPV.put(ppn, -1);
		}
		swapfile = ThreadedKernel.fileSystem.open("swapfile",true);//open the swapfile
  		freeswappagelist = new LinkedList<Integer>();//store the free page number list
		swappagenumber = 0;
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		ThreadedKernel.fileSystem.remove("swapfile");
		swapfile.close();
		super.terminate();
		
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static Lock vmLock;

	//The Inverted page table here. In the VMKernel initialize it.
	public static HashMap <Integer,VMProcess> IPT;

	public static HashMap <Integer, Integer> IPV;

	public static int victimPage;

	public static OpenFile swapfile;

	public static LinkedList<Integer> freeswappagelist;
	
	public static int swappagenumber;
	
}
