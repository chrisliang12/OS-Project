package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

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
		super.initialize(args);
		victimPointer = 0;
		IPT = new IPTEntry[Machine.processor().getNumPhysPages()];
		for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
			IPT[i] = new IPTEntry(null, null, false);
		}
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		swapPagesLock = new Lock();
		swapLock = new Lock();
		clockLock = new Lock();
		pinCountLock = new Lock();
		pinCV = new Condition(pinCountLock);
		swapAvailablePages = new LinkedList<>();
		swapAvailablePages.addLast(0);
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
		swapFile.close();
		ThreadedKernel.fileSystem.remove("swapFile");
		super.terminate();
	}

	/**
	 * choosing a page to evict
	 * @return ppn to evict
	 */
	public static int clock() {
		clockLock.acquire();
		int ppnNum = Machine.processor().getNumPhysPages();
		Lib.debug(dbgProcess, "--Victim (before clock algo): " + victimPointer);
		while (IPT[victimPointer].isPinned 
				|| IPT[victimPointer].entry.used) {
			IPT[victimPointer].entry.used = false;
			victimPointer = (victimPointer + 1) % ppnNum;
			Lib.debug(dbgProcess, "--Victim: " + victimPointer);
			Lib.debug(dbgProcess, "--isPinned: " + IPT[victimPointer].isPinned);
			Lib.debug(dbgProcess, "--isUsed: " + IPT[victimPointer].entry.used);
			Lib.debug(dbgProcess, "--isReadOnly: " + IPT[victimPointer].entry.readOnly);
		}
		int toEvictPPN = victimPointer;
		victimPointer = (victimPointer + 1) % ppnNum;
		clockLock.release();
		return toEvictPPN;
	}

	/**
	 * read a page from swapFile (page at spn) to 
	 * the physical memory (ppn)
	 * @param spn
	 * @param ppn
	 * @return # of bytes read or -1 if swapFile.read() fail
	 */
	public static int swapIn(int spn, int ppn) {
		swapLock.acquire();
		int pos = spn * Processor.pageSize;
		byte[] buf = Machine.processor().getMemory();
		int offset = Processor.makeAddress(ppn, 0);
		int length = Processor.pageSize;
		int numBytesRead = swapFile.read(pos, buf, offset, length);
		swapLock.release();
		return numBytesRead;
	}

	/**
	 * evict the page to swapFile
	 * @param ppn
	 * @return numBytesWrite or -1 if swapFile.write() fail
	 */
	public static int swapOut(int ppn) {
		swapLock.acquire();
		int spn = getAvailableSPN();
		Lib.debug(dbgProcess, "---SWAP OUT Target SPN: " + spn);
		byte[] physicalMem = Machine.processor().getMemory();
		int offset = Processor.makeAddress(ppn, 0);
		int length = Processor.pageSize;
		int numBytesWrite = swapFile.write(spn * length, physicalMem, offset, length);
		IPT[ppn].entry.vpn = spn; // indicate the spn in swapFile
		swapLock.release();
		return numBytesWrite;
	}

	/**
	 * if there is available spn, return it
	 * else create a new spn by return the file.length() / pageSize
	 * @return spn - a available spn to swapout
	 */
	public static int getAvailableSPN() {
		int spn = -1;
		swapPagesLock .acquire();
		if (swapAvailablePages.size() > 0) spn = swapAvailablePages.removeFirst();
		else spn = swapFile.length() / Processor.pageSize;
		swapPagesLock.release();
		return spn;
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	private static final char dbgProcess = 'a';
	
	public static int victimPointer; // ppn pointer used in clock algorithm

	public static OpenFile swapFile;

	public static LinkedList<Integer> swapAvailablePages; // available pages in swapFile

	public static Lock swapPagesLock; // lock for swapAvailablePages

	public static Lock swapLock; // lock for swapFile

	public static IPTEntry[] IPT; // inverted page table

	public static Lock clockLock; // lock for clock algorithm (victimPointer)

	public static Lock pinCountLock; // lock for pinCount

	public static Condition pinCV; // Condition Variable for pinned pages

	public static int pinCount = 0; // # of pages are currently pinned

	public class IPTEntry {
		public VMProcess process; // seems no need to reference process here
		public TranslationEntry entry;
		public boolean isPinned;

		public IPTEntry(VMProcess proc, TranslationEntry entry, boolean isPinned) {
			this.process = proc;
			this.entry = entry;
			this.isPinned = isPinned;
		}
	}
}
