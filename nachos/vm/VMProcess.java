package nachos.vm;

import java.util.Arrays;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0)
			return 0;

		int numBytesToRead = length;
		int vpn = Processor.pageFromAddress(vaddr);
		int offsetRead = Processor.offsetFromAddress(vaddr);
		int pAddrRead = -1;
		int numBytesHasRead = 0;
		VMKernel.pinCountLock.acquire();
		while (numBytesToRead > 0) {
			if (vpn >= pageTable.length || vpn < 0) {
				break;
			}
			if (!pageTable[vpn].valid) {
				handlePageFault(vpn);
			}
			if (pageTable[vpn].valid) {
				VMKernel.IPT[pageTable[vpn].ppn].isPinned = true;
				VMKernel.pinCount++;
				pageTable[vpn].used = true;
				pAddrRead = pageTable[vpn].ppn * pageSize + offsetRead;
			} 

			// if p address for reading is not in valid range return # of bytes has read;
			if (pAddrRead < 0 || pAddrRead >= memory.length) {
				VMKernel.IPT[pageTable[vpn].ppn].isPinned = false;
				VMKernel.pinCount--;
				VMKernel.pinCV.wakeAll();
				break;
			}

			int amount = Math.min(numBytesToRead, pageSize - offsetRead);
			System.arraycopy(memory, pAddrRead, data, offset, amount);
			pageTable[vpn].used = true; // set used bit
			VMKernel.IPT[pageTable[vpn].ppn].isPinned = false; // set IPTEntry.isPinned
			VMKernel.pinCount--;
			VMKernel.pinCV.wakeAll();
			numBytesToRead -= amount;
			numBytesHasRead += amount;
			offset += amount; // this is start idx to write in data array
			if (offsetRead != 0)
				offsetRead = 0; // only first read has a reading offset (v mem is continuous)
			vpn++;
			pAddrRead = -1;
		}
		VMKernel.pinCountLock.release();
		return numBytesHasRead;
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		int offsetWrite = Processor.offsetFromAddress(vaddr);
		int numBytesToWrite = length;
		int numBytesHasWritten = 0;
		int pAddrWrite = -1;
		VMKernel.pinCountLock.acquire();
		while (numBytesToWrite > 0) {
			if (vpn >= pageTable.length || vpn < 0) {
				break;
			}
			if (!pageTable[vpn].valid) {
				handlePageFault(vpn);
			}
			if (pageTable[vpn].valid) {
				if (!pageTable[vpn].readOnly) {
					VMKernel.IPT[pageTable[vpn].ppn].isPinned = true;
					VMKernel.pinCount++;
					pageTable[vpn].used = true;
					pAddrWrite = pageTable[vpn].ppn * pageSize + offsetWrite;
				} else {
					break;
				}
			} 

			if (pAddrWrite < 0 || pAddrWrite >= memory.length) {
				VMKernel.IPT[pageTable[vpn].ppn].isPinned = false;
				VMKernel.pinCount--;
				VMKernel.pinCV.wakeAll();
				break;
			}

			int amount = Math.min(numBytesToWrite, pageSize - offsetWrite);
			System.arraycopy(data, offset, memory, pAddrWrite, amount);
			pageTable[vpn].dirty = true; // set dirty bit
			pageTable[vpn].used = true; // set used bit
			VMKernel.IPT[pageTable[vpn].ppn].isPinned = false; // set IPTEntry.isPinned
			VMKernel.pinCount--;
			VMKernel.pinCV.wakeAll();
			numBytesHasWritten += amount;
			numBytesToWrite -= amount;
			offset += amount;
			if (offsetWrite != 0)
				offsetWrite = 0;
			vpn++;
			pAddrWrite = -1;
		}
		VMKernel.pinCountLock.release();

		return numBytesHasWritten;
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {

		// initialize pageTable by preallocation the physical page frames
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			// mark it as invalid to trigger a page fault
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Currently only handle page fault caused by invalid TranslationEntry.
	 * Loading the corresponding page to the memory
	 * 
	 * @param vpn - bad vpn derived from Processor.pageFromAddress(badVAddr)
	 */
	private void handlePageFault(int vpn) {
		//System.out.println("--------------Handling Page Fault----------------");
		int ppn = VMKernel.getAvailablePPN();
		if (ppn == -1) {
			//System.out.println("----------No available physical page frame------------");
			// VMKernel.pinCountLock.acquire();
			// while (VMKernel.pinCount == Machine.processor().getNumPhysPages()) {
			// 	Lib.debug(dbgProcess, "------ALL PAGES ARE PINNED----");
			// 	VMKernel.pinCV.sleep();
			// }
			int toEvictPPN = VMKernel.clock();
			VMKernel.IPT[toEvictPPN].entry.valid = false;
			// VMKernel.pinCountLock.release();
			
			// if the entry is dirty swap out
			// else toEvictPPN can be immediately used
			if (VMKernel.IPT[toEvictPPN].entry.dirty) {
				// get a free page in swapFile (if no free page, create new one)
				// evict the toEvictPPN to swap file
				Lib.debug(dbgProcess, "---SWAP FILE LENGTH: " + VMKernel.swapFile.length());
				int numBytesWrite = VMKernel.swapOut(toEvictPPN);
				Lib.assertTrue(numBytesWrite != -1, "swap out fail");
			}

			if (pageTable[vpn].dirty) {
				Lib.debug(dbgProcess, "------------SWAP IN----------- ");
				// swap in that page
				int numBytesRead = VMKernel.swapIn(pageTable[vpn].vpn, toEvictPPN);
				Lib.assertTrue(numBytesRead != -1, "swap in fail");
				pageTable[vpn] = new TranslationEntry(vpn, toEvictPPN, true, false, true, true);
				// set IPTEntry for toEvictPPN point to the entry causing the current page fault
				VMKernel.IPT[toEvictPPN].entry = this.pageTable[vpn]; 
				return;
			} else {
				ppn = toEvictPPN;
			}

		}
		pageTable[vpn].ppn = ppn;
		VMKernel.IPT[ppn].entry = this.pageTable[vpn];

		if (!pageTable[vpn].valid) {
			Lib.debug(dbgProcess, "\tcurr fault vpn: " + vpn);
			// loop through all sections to find the corresponding vpn
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName()
						+ " section (" + section.getLength() + " pages)");

				// if the vpn is in the range of curr section's vpn range, we find it
				if (vpn >= section.getFirstVPN() && vpn < section.getFirstVPN() + section.getLength()) {
					section.loadPage(vpn - section.getFirstVPN(), pageTable[vpn].ppn);
					if (section.isReadOnly())
						pageTable[vpn].readOnly = true;
					pageTable[vpn].valid = true;
				}

				// if vpn is out of all section's length, just mark it as valid
				// In this case, super.load() will zero-fill the page???
				if (s == coff.getNumSections() - 1 && vpn >= section.getFirstVPN() + section.getLength() && vpn < numPages) {
					pageTable[vpn].valid = true;
					System.arraycopy(new byte[Processor.pageSize], 0, Machine.processor().getMemory(), Processor.makeAddress(pageTable[vpn].ppn, 0), Processor.pageSize);
				}
			}
		}
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			/**
			 * handle page fault exceptions to prepare the requested page on
			 * demand since we mark every TranslationEntry as invalid
			 * when initializing the pageTable
			 */
			case Processor.exceptionPageFault:
				// get badVAddr at which pageFault occured
				int badVAddr = Machine.processor().readRegister(Processor.regBadVAddr);
				// handle page fault, pass in the bad vpn
				handlePageFault(Processor.pageFromAddress(badVAddr));
				break;
			default:
				super.handleException(cause);
				break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
