package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.*;

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
	private UserProcess parent;
	private LinkedList <Integer> ChildrenPID;
	private HashMap <Integer,UserProcess> childid_to_childprocess;
	public int current_process_id;
	private ArrayList<OpenFile> fileDescriptor;
	private LinkedList<Integer> freeList;
	private int childstatusPID;
	public UserProcess() {
		UserKernel.Processlock.acquire();
		current_process_id = UserKernel.next_process_id;
		UserKernel.next_process_id ++;
		UserKernel.Processlock.release();
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		fileDescriptor = new ArrayList<OpenFile>(Collections.nCopies(16, null));
		fileDescriptor.set(0, UserKernel.console.openForReading());
		fileDescriptor.set(1, UserKernel.console.openForWriting());
		childid_to_childprocess = new HashMap <Integer,UserProcess>();
		ChildrenPID = new LinkedList <Integer> ();
		freeList = UserKernel.freeList;
		
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
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
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
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
	 * @param data the array where the data will be stored.
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
		
		int transferredBytes = 0;
		int amount = 0;
		// byte[] memory = Machine.processor().getMemory();

		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);
		byte[] memory = Machine.processor().getMemory();
		while(length > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int vpn_offset = Processor.offsetFromAddress(vaddr);

			if (pageTable[vpn] == null){
				return 0;
			}
			int ppn = pageTable[vpn].ppn;
			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize);

			if (physcial_address < 0 || physcial_address >= memory.length)
				return 0;

			System.arraycopy(memory, physcial_address, data, offset, amount);

			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			//how about the offset, very confused on this part.
		}
		

		return transferredBytes;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
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
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		// byte[] memory = Machine.processor().getMemory();

		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(data, offset, memory, vaddr, amount);

		// return amount;
		int transferredBytes = 0;
		int amount = 0;
		System.out.println("The length (readBytes) is: " + length);

		byte[] memory = Machine.processor().getMemory();
		while(length > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int vpn_offset = Processor.offsetFromAddress(vaddr);

			if (pageTable[vpn].readOnly){
				//System.out.println("The reason is the page table is read only");
				return 0;
			}

			if (pageTable[vpn] == null){
				//System.out.println("The reason is the page is null.");
				return 0;
			}
			int ppn = pageTable[vpn].ppn;
			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize);

			if (physcial_address < 0 || physcial_address >= memory.length){
				//System.out.println("The reason is the pm is invalid.");
				return 0;
			}
			System.arraycopy(data, offset, memory, vaddr, amount);

			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			//how about the offset, very confused on this part.
		}
		System.out.println("The transferred Bytes(write bytes): " + transferredBytes);

		return transferredBytes;
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
		}
		catch (EOFException e) {
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
		UserKernel.lock.acquire();
		if (numPages > freeList.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.lock.release();
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int vpn = 0; vpn < numPages; vpn++){
			int ppn = freeList.get(vpn);
			pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		}

		
		// load sections, still confused on how this one works.
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");
			//need to think about the section, ifz one section is readonly, what the pageTable readonly will be.
			
			boolean readOnly = section.isReadOnly();
			// for (int vpn = 0; vpn < numPages; vpn++){
			// 	pageTable[vpn].readOnly = readOnly;
			// }
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int ppn = freeList.remove();
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly, false, false);
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		UserKernel.lock.release();

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.lock.acquire();

		for (int i = 0; i < numPages; i++){
			freeList.add(pageTable[i].ppn);
		}

		UserKernel.lock.release();
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
		if(current_process_id!=0){
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleCreate(int name){
		//read from virtual memory
		String virtualmemoryString = readVirtualMemoryString(name, 256);
		if (virtualmemoryString == null){
			return -1;
		}
		OpenFile openfile = ThreadedKernel.fileSystem.open(virtualmemoryString, true);

		if (openfile == null){
			return -1;
		}

		int fd_entry = 0;
		for (int i = 0; i < 16; i++){
			if (fileDescriptor.get(i) == null){
				fd_entry = i;
				fileDescriptor.set(i, openfile);
				break;
			}
		}


		return fd_entry;
	} 

	private int handleOpen(int name){
		String virtualmemoryString = readVirtualMemoryString(name, 256);
		if (virtualmemoryString == null){
			return -1;
		}
		OpenFile openfile = ThreadedKernel.fileSystem.open(virtualmemoryString, false);

		if (openfile == null){
			return -1;
		}

		int fd_entry = 0;
		for (int i = 0; i < 16; i++){
			System.out.println("The length of fd is: " + fileDescriptor.size());
			if (fileDescriptor.get(i) == null){
				fd_entry = i;
				fileDescriptor.set(i, openfile);
				break;
			}
		}


		return fd_entry;
	}


	private int handleClose(int fd){
		if (fd < 0 || fd > 15){
			return -1;
		}

		if (fileDescriptor.get(fd) == null){
			return -1;
		}

		OpenFile openfile = fileDescriptor.get(fd);
		openfile.close();
		fileDescriptor.set(fd, null);

		return 0;
	}

	private int handleUnlink(int name){
		String virtualmemoryString = readVirtualMemoryString(name, 256);
		if (virtualmemoryString == null){
			return -1;
		}

		if (!ThreadedKernel.fileSystem.remove(virtualmemoryString)){
			return -1;
		}
		return 0;
	}

	private int handleRead(int fd, int buffer_address, int count){
		
		if (count < 0){
			return -1;
		}

		//check the buffer_address
		if (buffer_address < 0 || buffer_address >= (numPages * pageSize)){
			return -1;
		}

		if ((buffer_address + count) >= (numPages * pageSize)){
			return -1;
		}
		//check the file Descriptor part.
		if (fd < 0 || fd > 15){
			return -1;
		}

		if (fileDescriptor.get(fd) == null){
			return -1;
		}

		//return how many bytes of readBytes we have.
		int transferredBytes = 0;
		int readBytes = 0;
		int writeBytes = 0;
		int amount = 0;

		//That's the buffer bytes that we use for storing read bytes for every time.
		byte[] buf = new byte[pageSize];

		while(count > 0){
			if (count < pageSize){
				amount = count;
			}else {
				amount = pageSize;
			}
			// if the file will reach to the end before amount of data, it will read as much as possible bytes in the file.
			//Thus, the readBytes is not equal to the amount for some time.
			readBytes = fileDescriptor.get(fd).read(buf, 0, amount);
			if (readBytes == -1)
				return -1;
			
			writeBytes = writeVirtualMemory(buffer_address, buf, 0, readBytes);

			//we are not successfully transfer data.
			if (writeBytes != readBytes){
				//System.out.println("That's the reason that fail in the writeVirtualMemory: writeBytes != readBytes.");
				return -1;
			}
			
			//update count, buffer_address, transferredBytes.

			count -= amount;
			transferredBytes += readBytes;
			buffer_address += amount;

			// means we already read to the end of the file.
			if (readBytes < amount){
				break;
			}

		}
		return transferredBytes;
	}


	private int handleWrite(int fd, int buffer_address, int count){
		//System.out.println("----------------------------------------");
		//check the count, is it possible that the count is 0?
		if (count < 0){
			return -1;
		}

		//check the buffer_address
		if (buffer_address < 0 || buffer_address >= (numPages * pageSize)){
			return -1;
		}

		if ((buffer_address + count) >= (numPages * pageSize)){
			return -1;
		}
		//check the file Descriptor part.
		if (fd < 0 || fd > 15){
			return -1;
		}

		if (fileDescriptor.get(fd) == null){
			return -1;
		}
		//System.out.println("after edge case in write.");
		//return how many bytes of readBytes we have.
		int transferredBytes = 0;
		int readBytes = 0;
		int writeBytes = 0;
		int amount = 0;

		//That's the buffer bytes that we use for storing read bytes for every time.
		byte[] buf = new byte[pageSize];
		int byte_count = count;

		while(byte_count > 0){
			if (byte_count < pageSize){
				amount = byte_count;
			}else{
				amount = pageSize;
			}
			readBytes = readVirtualMemory(buffer_address, buf, 0, amount);
			// if (readBytes == 0){
			// 	System.out.println("It has error in vm.");
			// }
			//System.out.println("Read Bytes is: " + readBytes);

			writeBytes = fileDescriptor.get(fd).write(buf, 0, amount);
			//System.out.println("The WB is: " + writeBytes);
			if (writeBytes == -1)
				return -1;
		
			//we are not successfully transfer data.
			if (writeBytes != readBytes){
				return -1;
			}
			
			//update count, buffer_address, transferredBytes.
			//System.out.println("before updating, the count is: " + byte_count);
			byte_count -= amount;
			transferredBytes += writeBytes;
			buffer_address += amount;
			//System.out.println("Now, the count is: " + byte_count);

			// means we already reach to the end of the file.
			if (writeBytes < amount){
				break;
			}

		}
		return transferredBytes;
	}

	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int processID, int status_addr){
		UserKernel.lock.acquire();
		if(this.childid_to_childprocess.get(processID)==null){
			UserKernel.lock.release();
			return -1;
		}
		UThread childProcess = childid_to_childprocess.get(processID).thread;
		UserKernel.lock.release();
		childProcess.join();
		UserKernel.lock.acquire();
		byte[] array = new byte[4];
		Lib.bytesFromInt(array, 0, this.childstatusPID);
		writeVirtualMemory(status_addr, array);
		if (this.childstatusPID==0){
			UserKernel.lock.release();
			return 1;
		}
		else{
			UserKernel.lock.release();
			return 0;
		}
	}

	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int vaddr,int argc,int argv){
		String filename = readVirtualMemoryString(vaddr,256);	//get the file name by start reading from the first address of the filename
//Still need to handle corner cases...wait to be done
		//System.out.println(filename);
		String[] arguments = new String[argc];
		byte [] temp = new byte [4];
		for (int i = 0; i < argc; i++){
			
			readVirtualMemory(argv+i*4,temp);
			int addr = Lib.bytesToInt(temp,0);
			//System.out.println(readVirtualMemoryString(addr,256));
			arguments[i] = readVirtualMemoryString(addr,256);
		}
		UserProcess child = UserProcess.newUserProcess();
		child.parent = this;
		if (child.execute(filename,arguments)){
			//userKernel.numProcessLock.acquire();
			
			this.ChildrenPID.add(child.current_process_id);
			childid_to_childprocess.put(child.current_process_id,child);
			return child.current_process_id;
		}
		UserKernel.Processlock.acquire();
		UserKernel.num_process --;
		UserKernel.Processlock.release();
		//readVirtualMemory
		return -1; 	
	}
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		if (this.parent != null){
			this.parent.ChildrenPID.remove(this.current_process_id);
			this.parent.childid_to_childprocess.remove(this.current_process_id);
		}
		for (Map.Entry<Integer, UserProcess> entry : childid_to_childprocess.entrySet()) {
			entry.getValue().parent = null;
		}
		this.unloadSections();
		for (int i = 0; i < 15; i++){
			handleClose(i);
		}
		if (this.parent != null){
			parent.childstatusPID = status;
		}
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		 if (UserKernel.num_process == 1){
			 Kernel.kernel.terminate();
		 } else{
			this.thread.finish();
		}


		return 0;
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
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
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
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallOpen:
			return handleOpen(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallExec:
			return handleExec(a0, a1, a2);
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
		//System.out.println("the cause is " + cause);
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
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
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
}
