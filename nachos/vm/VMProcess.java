package nachos.vm;

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
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		VMKernel.vmLock.acquire();
		System.out.println("number of pages is " + numPages);
		System.out.println("freelist length is " + freeList.size());
		if (numPages > freeList.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.lock.release();
			System.out.println("False");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		for (int vpn = 0; vpn < numPages; vpn++){
			pageTable[vpn] = new TranslationEntry(vpn, -1, false, false, false, false);
		}
		VMKernel.vmLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
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
		case 1:
			System.out.println("We enter the handle exception.");
			handlePageFault(processor.readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	public void handlePageFault(int badVaddr){
		Processor processor = Machine.processor();
		int badVpn = Processor.pageFromAddress(badVaddr);
		System.out.println("badvpn" + badVpn);
		/* loop through all sections to check if it is a coff page */
		int ppn = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean readOnly = section.isReadOnly();
			if (badVpn >= section.getFirstVPN() && badVpn < (section.getFirstVPN() + section.getLength())){
				System.out.println("This is the coff page.");
				for (int i = 0; i < section.getLength(); i++) {
					if (section.getFirstVPN() + i == badVpn){
						int vpn = section.getFirstVPN() + i;
						//TODO: do your page replacement algorithm here:
						if (!freeList.isEmpty()){
							ppn = freeList.remove();
						} else{
							ppn = PageReplacement(vpn);
						}				
						pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly, false, false);
						section.loadPage(i, pageTable[vpn].ppn);
						return; 
					}
				}
			}
		}

		if (!freeList.isEmpty()){
			ppn = freeList.remove();
		} else{
			ppn = PageReplacement(badVpn);
		}	
		byte[] data = new byte[Processor.pageSize];
		pageTable[badVpn] = new TranslationEntry(badVpn, ppn, true, false, false, false);
		System.arraycopy(data,0,processor.getMemory(),processor.makeAddress(ppn,0),Processor.pageSize);
		/* If this vpn is a coff page */
		
		return;
	}


	// TODO: finish the pagereplacement algorithm here. Also see the Inverted page table in VMKernel. I also have the IPT_vpn. see the VMprocess in the end.
	private int PageReplacement(int vpn){

		return 0;
	}


	//TODO: finish the swapping part
	//if we find this ppn is the victim page we need to remove, and dirty bit is 1, then we move to swapping file.
	//if you have more time on it. Please do the part in readVirtualMemory. If we find the page is invalid, and dirty is 1,
	//we need to search from sweaping file. Also writeVirtualMemory.
	private void swap(int ppn){
		
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.println("Can we in the write VM.");
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
		//System.out.println("The length (readBytes) is: " + length);
		//String string = new String(data);

		//System.out.println("In the writeVirtualMemeory the data will be: " + string);
		byte[] memory = Machine.processor().getMemory();
		while(length > 0){
			int vpn = Processor.pageFromAddress(vaddr);
			int vpn_offset = Processor.offsetFromAddress(vaddr);

			if (pageTable[vpn].readOnly){
				//System.out.println("The reason is the page table is read only");
				return -1;
			}

			if (pageTable[vpn] == null){
				//System.out.println("The reason is the page is null.");
				return -1;
			}
			int ppn = pageTable[vpn].ppn;
			if(ppn == -1){
				handlePageFault(vaddr);
			}
			/*Change dirty bit to 1 */
			pageTable[vpn].dirty = true;
			ppn = pageTable[vpn].ppn;
			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize-vpn_offset);

			if (physcial_address < 0 || physcial_address >= memory.length){
				//System.out.println("The reason is the pm is invalid.");
				return -1;
			}
			System.arraycopy(data, offset, memory, physcial_address, amount);
			//System.out.println("The offset will be: " + offset + " and in this offset, we transfer the");

			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			//how about the offset, very confused on this part.
		}
		//System.out.println("The transferred Bytes(write bytes): " + transferredBytes);

		return transferredBytes;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.println("can we in the readvirtualmemory.");
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
				return -1;
			}
			int ppn = pageTable[vpn].ppn;
			if(ppn == -1){
				handleException(Processor.exceptionPageFault);
			}
			ppn = pageTable[vpn].ppn;
			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize-vpn_offset);

			if (physcial_address < 0 || physcial_address >= memory.length)
				return -1;

			System.arraycopy(memory, physcial_address, data, offset, amount);

			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			//how about the offset, very confused on this part.
		}
		

		return transferredBytes;
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private int IPT_vpn;
}