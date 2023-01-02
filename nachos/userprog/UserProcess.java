package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.lang.Math;
import java.util.HashMap;
import java.util.Map;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// 	pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		fd = new OpenFile[FILES_NUM];
		fd[0] = UserKernel.console.openForReading();
		fd[1] = UserKernel.console.openForWriting();

		UserKernel.numProcessLock.acquire();
		UserKernel.numProcess++;
		UserKernel.numProcessLock.release();

		System.out.println("UserProcess: " + UserKernel.numProcess);

		children = new HashMap<UserProcess, Integer>();
		parent = null;
		pid = UserKernel.getNextPID();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int numBytesToRead = length;
		int vpn = Processor.pageFromAddress(vaddr);
		int offsetRead = Processor.offsetFromAddress(vaddr);
		int pAddrRead = -1;
		int numBytesHasRead = 0;

		while (numBytesToRead > 0) {
			for (int i = 0; i < pageTable.length; i++) {
				if (vpn == pageTable[i].vpn && pageTable[i].valid) {
					pAddrRead = pageTable[i].ppn * pageSize + offsetRead;
					break;
				}
			}

			// if p address to read is not in valid range return # of bytes has read;
			if (pAddrRead < 0 || pAddrRead >= memory.length) return numBytesHasRead;
			int amount = Math.min(numBytesToRead, pageSize - offsetRead);
			System.arraycopy(memory, pAddrRead, data, offset, amount);
			numBytesToRead -= amount;
	    	numBytesHasRead += amount;
			offset += amount; // this is start idx to write in data array
			if (offsetRead != 0) offsetRead = 0; // only first read has a reading offset (v mem is continuous)
			vpn++;
			pAddrRead = -1;
		}

		return numBytesHasRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
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
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		int offsetWrite = Processor.offsetFromAddress(vaddr);
		int numBytesToWrite = length;
		int numBytesHasWritten = 0;
		int pAddrWrite = -1;

		while (numBytesToWrite > 0) {
			for (int i = 0; i < numPages; i++) {
				if (vpn == pageTable[i].vpn && pageTable[i].valid && !pageTable[i].readOnly) {
					pAddrWrite = pageTable[i].ppn * pageSize + offsetWrite;
					break;
				}
			}

			if (pAddrWrite < 0 || pAddrWrite >= memory.length) break;
			int amount = Math.min(numBytesToWrite, pageSize - offsetWrite);
			System.arraycopy(data, offset, memory, pAddrWrite, amount);
			numBytesHasWritten += amount;
			numBytesToWrite -= amount;
			offset += amount;
			if (offsetWrite != 0) offsetWrite = 0;
			vpn++;
			pAddrWrite = -1;
		}

		return numBytesHasWritten;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// initialize pageTable
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			// get an single available ppn from kernel
			int availablePPN = UserKernel.getAvailablePPN();
			if (availablePPN < 0) return false;
			pageTable[i] = new TranslationEntry(i, availablePPN, true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
				if (section.isReadOnly())
					pageTable[vpn].readOnly = true;

			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++) {
			UserKernel.releasePPN(pageTable[i].ppn);
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (pid != 0)
			return -1;

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		System.out.println("UserProcess.handleExit (" + status + ")");

		for (int i = 2; i < FILES_NUM; i++) {
			if (fd[i] != null) {
				fd[i].close();
				fd[i] = null;
			}
		}
		unloadSections();
		coff.close();

		// need to consider children processes
		// remove the children's parent pointer to this process

		// need to consider parent processes
		// need to wake parent process if it is waiting for this process to finish
		if (parent != null) {
			this.parent.children.replace(this, status);
		}

		// if this is the last process, also terminate the kernel
		UserKernel.numProcessLock.acquire();
		if (UserKernel.numProcess == 1) {
			UserKernel.numProcess--;
			UserKernel.numProcessLock.release();
			Kernel.kernel.terminate();
		} else {
			UserKernel.numProcess--;
			UserKernel.numProcessLock.release();
		}

		KThread.finish();
		return 0;
	}

	private int handleCreate(int address) {
		String fileName = readVirtualMemoryString(address, PARAM_LENGTH);

		OpenFile file = Machine.stubFileSystem().open(fileName, true);
		if (file == null) {
			return -1;
		}
		return appendToFD(file);
	}

	private int handleOpen(int address) {
		String fileName = readVirtualMemoryString(address, PARAM_LENGTH);

		OpenFile file = Machine.stubFileSystem().open(fileName, false);
		if (file == null) {
			return -1;
		}
		return appendToFD(file);
	}

	private int handleClose(int fd) {
		if (fd < 0 || fd >= FILES_NUM)
			return -1; // edge case to catch

		if (this.fd[fd] == null) { // or already closed file
			return -1;
		}

		this.fd[fd].close();
		this.fd[fd] = null;

		return 0;
	}

	private int handleUnlink(int address) {
		String fileName = readVirtualMemoryString(address, PARAM_LENGTH);
		if (fileName == null) {
			return -1;
		}
		if (!Machine.stubFileSystem().remove(fileName)) {
			return -1;
		}
		return 0;
	}

	private int bytesLeftToRead(int total, int alreadyRead, int bufferSize) {
		int leftBytes = total - alreadyRead;
		return Math.min(leftBytes, bufferSize);
	}

	private int getPagedBufferSize(int totalRead) {
		return Math.min(PAGE_SIZE, totalRead);
	}

	private int handleRead(int fd, int bufferAddr, int totalRead){
		if (fd <0 || fd >= FILES_NUM || totalRead < 0) { // edge case with fd or size
			return -1;
		}
		if(totalRead == 0) { // no need to read
			return 0;
		}

		OpenFile file = this.fd[fd];
		if (file == null) {
			return -1;
		}

		int bufferSize = getPagedBufferSize(totalRead); // buffered size array
		byte []localBuffer = new byte[bufferSize];

		int alreadyRead = 0;
		while (alreadyRead < totalRead) {
			int leftBytes = bytesLeftToRead(totalRead, alreadyRead, bufferSize);

			int readBytesFromFile = file.read(localBuffer, 0, leftBytes);
			if (readBytesFromFile == -1){
				return -1;
			}

			int writeBytesToVM = writeVirtualMemory(bufferAddr + alreadyRead, localBuffer, 0, readBytesFromFile);
			if (writeBytesToVM == -1 || writeBytesToVM != readBytesFromFile) {
				return alreadyRead;
			}

			alreadyRead += writeBytesToVM;

			if (readBytesFromFile < bufferSize) {
				break;
			}
		}
		return alreadyRead;
	}
	private int handleWrite(int fd, int bufferAddr, int totalRead){
		if(fd < 0 || fd >= FILES_NUM || totalRead < 0)
			return -1;
		if (totalRead == 0) {
			return 0;
		}

		OpenFile file = this.fd[fd];
		if (file == null) {
			return -1;
		}

		if(bufferAddr < 0 || bufferAddr > pageTable.length * pageSize) {
			return -1;
		}

		int bufferSize = getPagedBufferSize(totalRead);
		byte[] localBuffer = new byte[bufferSize];

		int alreadyRead = 0;
		while (alreadyRead < totalRead){
			int leftBytes = bytesLeftToRead(totalRead, alreadyRead, bufferSize);

			int readBytesFromVM = readVirtualMemory(bufferAddr + alreadyRead, localBuffer, 0, leftBytes);
			if (readBytesFromVM == -1)
				return -1; // error in readVirtualMemory

			int writeBytesToFile = file.write(localBuffer, 0, readBytesFromVM);
			if (writeBytesToFile==-1 || writeBytesToFile != readBytesFromVM)
				return alreadyRead;

			alreadyRead += writeBytesToFile;

			if(readBytesFromVM<bufferSize) {
				break;
			}
		}

		if(alreadyRead < totalRead)
			return -1;

		return alreadyRead;
	}

	private int appendToFD(OpenFile file) {
		// find next available position
		int nextPosition = FIRST_AVAILABLE_FD;
		for (; nextPosition < FILES_NUM; nextPosition++) {
			if (fd[nextPosition] == null) {
				break;
			}
		}

		if (nextPosition == FILES_NUM) {
			return -1;
		}

		fd[nextPosition] = file;
		return nextPosition;
	}

	
	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int file, int argc, int argv) {
		System.out.println("UserProcess.handleExec (" + file + ", " + argc + ", " + argv + ")");
		
		String filename = readVirtualMemoryString(file, PARAM_LENGTH);
		if (filename == null) {
			System.out.println("UserProcess.handleExec: filename is null");
			return -1;
		}
		if (!filename.endsWith(".coff")) {
			System.out.println("UserProcess.handleExec: filename does not end with .coff");
			return -1;
		}

		String[] args = new String[argc];
		byte[] buffer = new byte[4];
		for (int i = 0; i < argc; i++) {
			if (readVirtualMemory(argv + i * 4, buffer) != 4) {
				System.out.println("UserProcess.handleExec: readVirtualMemory failed");
				return -1;
			}
			int argAddr = Lib.bytesToInt(buffer, 0);
			args[i] = readVirtualMemoryString(argAddr, PARAM_LENGTH);
			if (args[i] == null) {
				System.out.println("UserProcess.handleExec: args[" + i + "] is null");
				return -1;
			}
		}

		UserProcess child = UserProcess.newUserProcess();
		children.put(child, 1);
		child.parent = this;
		if (!child.execute(filename, args)) {
			System.out.println("UserProcess.handleExec: execute failed");
			return -1;
		}

		return child.pid;
	}

	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int processID, int status) {
		System.out.println("UserProcess.handleJoin (" + processID + ", " + status + ")");
		
		UserProcess child = null;
		for (UserProcess p : children.keySet()) {
			if (p.pid == processID) {
				child = p;
				break;
			}
		}
		if (child == null) {
			System.out.println("UserProcess.handleJoin: child is null");
			return -1;
		}

		child.thread.join();

		int exitStatus = children.get(child);
		children.remove(child);
		if (exitStatus == -10) return 0;
		byte[] buffer = new byte[4];
		Lib.bytesFromInt(buffer, 0, exitStatus);
		if (writeVirtualMemory(status, buffer) != 4) {
			System.out.println("UserProcess.handleJoin: writeVirtualMemory failed");
			return -1;
		}

		return exitStatus;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 *
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				// System.out.println("Unexpected exception: " + Processor.exceptionNames[cause]);
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				handleExit(-10);
				Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	// newly added variable
	private final int FILES_NUM = 32;
	private final int PARAM_LENGTH = 256;
	private final int FIRST_AVAILABLE_FD = 2;

	private final int PAGE_SIZE = Processor.pageSize;
	private OpenFile[] fd;

	private int pid;
	private UserProcess parent;
	private Map<UserProcess, Integer> children;

}
