package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

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
		//VMKernel.vmLock.acquire();
		System.out.println("number of pages is " + numPages);
		System.out.println("freelist length is " + freeList.size());
		// if (numPages > freeList.size()) {
		// 	coff.close();
		// 	Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// 	UserKernel.lock.release();
		// 	System.out.println("False");
		// 	return false;
		// }

		pageTable = new TranslationEntry[numPages];
		for (int vpn = 0; vpn < numPages; vpn++){
			pageTable[vpn] = new TranslationEntry(vpn, -1, false, false, false, false);
		}
		//VMKernel.vmLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++){
			if (pageTable[i].ppn != -1){
				freeList.add(pageTable[i].ppn);
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
		case 1:
			//System.out.println("We enter the handle exception.");
			int badVpn = Processor.pageFromAddress(processor.readRegister(Processor.regBadVAddr));
			handlePageFault(badVpn);
			break;
		default:
			//System.out.println("The cause is: " + cause);
			super.handleException(cause);
			break;
		}
	}

	public void handlePageFault(int badVpn){
		VMKernel.vmLock.acquire();
		Processor processor = Machine.processor();
		//System.out.println("badvpn" + badVpn);
		/* loop through all sections to check if it is a coff page */
		int ppn = 0;
		// System.out.println("page table ppn is: " + pageTable[badVpn].ppn);
		// System.out.println("page table spn is: " + pageTable[badVpn].spn);
		// System.out.println("page table vaild is: " + pageTable[badVpn].valid);
		
		if (pageTable[badVpn].spn != -1){
				//read from swapfile.
				//System.out.println("any possible to read from swapfile.");
				int spn = pageTable[badVpn].spn;
				if (!freeList.isEmpty()){
					ppn = freeList.remove();
				} else{
					ppn = PageReplacement();
				}

				VMKernel.swapfile.read(spn*pageSize, processor.getMemory(), ppn*pageSize, pageSize);
				pageTable[badVpn].valid = true;
				pageTable[badVpn].ppn = ppn;
				VMKernel.IPT.replace(ppn, this);
				VMKernel.IPV.replace(ppn, badVpn);
				//check this part later. change dirty to false.
				VMKernel.freeswappagelist.add(spn);
				pageTable[badVpn].printString();
				pageTable[badVpn].dirty = false;
				pageTable[badVpn].spn = -1;
				
				VMKernel.vmLock.release();
				return;
			}
		if (badVpn >= numPages - stackPages - 1 && badVpn <= numPages - 1){
			//check it is in swap file
			//that's the stack/argument pages.
			if (!freeList.isEmpty()){
			ppn = freeList.remove();
			} else{
			ppn = PageReplacement();
			}	
			VMKernel.IPT.replace(ppn, this);
			VMKernel.IPV.replace(ppn, badVpn);
			byte[] data = new byte[Processor.pageSize];
			//pageTable[badVpn] = new TranslationEntry(badVpn, ppn, true, false, false, false);
			pageTable[badVpn].valid = true;
			pageTable[badVpn].ppn = ppn;
			System.arraycopy(data,0,processor.getMemory(),processor.makeAddress(ppn,0),pageSize);
			//pageTable[badVpn].printString();
			}
		else {
			//System.out.println("That's the coff section page.");
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean readOnly = section.isReadOnly();
				for (int i = 0; i < section.getLength(); i++) {
					if (section.getFirstVPN() + i == badVpn){
						int vpn = section.getFirstVPN() + i;
						//TODO: do your page replacement algorithm here:
						if (!freeList.isEmpty()){
							ppn = freeList.remove();
						} else{
							ppn = PageReplacement();
						}
						VMKernel.IPT.replace(ppn, this);
						VMKernel.IPV.replace(ppn, vpn);				
						//pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly, false, false);
						pageTable[vpn].valid = true;
						pageTable[vpn].readOnly = readOnly;
						pageTable[vpn].ppn = ppn;
						section.loadPage(i, pageTable[vpn].ppn);
						//System.out.println("The ppn in pagefault handler is: " + ppn + " and the vpn is: " + vpn);
						//System.out.println("That's the coff page.");
						//pageTable[vpn].printString();
					}
				}
			
		}
		}
		VMKernel.vmLock.release();
		return;
	}


	// TODO: finish the pagereplacement algorithm here. Also see the Inverted page table in VMKernel. I also have the IPT_vpn. see the VMprocess in the end.
	private int PageReplacement(){
		VMKernel.pinLock.acquire();
		//System.out.println("Oh, we enter the page replacement function.");
		int ppn = 0;
		int numPhysPages = Machine.processor().getNumPhysPages();
		while(true) {
            // invertedPageTable = <PPN, pair<process, VPN>>
            // if(VMKernel.pinTable[VMProcess.victimPage] == true) {
            //     continue;
            // }

			if(VMKernel.IPT.get(VMKernel.victimPage).pageTable[VMKernel.IPV.get(VMKernel.victimPage)].used == false) {
				
				ppn = VMKernel.victimPage;
				while(VMKernel.pinTable[ppn] == true){
					VMKernel.pinCV.sleep();
				}
				if (VMKernel.IPT.get(VMKernel.victimPage).pageTable[VMKernel.IPV.get(VMKernel.victimPage)].dirty == true){
					swap(ppn);
				}
				//System.out.println("what is the ppn in pagereplacement is: " + ppn);
				VMKernel.victimPage = (VMKernel.victimPage + 1) % numPhysPages;
                break;
            }
            else if(VMKernel.IPT.get(VMKernel.victimPage).pageTable[VMKernel.IPV.get(VMKernel.victimPage)].used == true) {
                VMKernel.IPT.get(VMKernel.victimPage).pageTable[VMKernel.IPV.get(VMKernel.victimPage)].used = false;
            }

            VMKernel.victimPage = (VMKernel.victimPage + 1) % numPhysPages;
        }
		VMKernel.IPT.get(ppn).pageTable[VMKernel.IPV.get(ppn)].ppn = -1;
		VMKernel.IPT.get(ppn).pageTable[VMKernel.IPV.get(ppn)].valid = false;
		VMKernel.pinLock.release();
		return ppn;
	}


	//TODO: finish the swapping part
	//if we find this ppn is the victim page we need to remove, and dirty bit is 1, then we move to swapping file.
	//if you have more time on it. Please do the part in readVirtualMemory. If we find the page is invalid, and dirty is 1,
	//we need to search from sweaping file. and put these data back to memory. set the page to valid. Same in writeVirtualMemory.
	private void swap(int ppn){
		System.out.println("Hey, I am in the swap now.");
		System.out.println("The page table vpn in swap is: " + VMKernel.IPT.get(ppn).pageTable[VMKernel.IPV.get(ppn)].vpn + "readonly is: " + VMKernel.IPT.get(ppn).pageTable[VMKernel.IPV.get(ppn)].readOnly);
		int spn = 0;
		if (!VMKernel.freeswappagelist.isEmpty()){//if there is free swap page number left, then use this
			spn = VMKernel.freeswappagelist.removeLast();
		}
		else{//else we make a new space
			spn = VMKernel.swappagenumber;//swapfile page number
			VMKernel.swappagenumber ++;//add one to spn
		}
			
		VMKernel.swapfile.write(spn*pageSize, Machine.processor().getMemory(), ppn*pageSize, pageSize);//write to swap file
		//VMkernel.IPT[ppn].entry.vpn = spn;//map from vpn to spn
		VMKernel.IPT.get(ppn).pageTable[VMKernel.IPV.get(ppn)].spn = spn;
		//VMkernel.IPT[victim].entry.valid = false;//set entry to false
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

			if (pageTable[vpn] == null){
				//System.out.println("The reason is the page is null.");
				return -1;
			}
			int ppn = pageTable[vpn].ppn;
			//ppn = -1, spn = anynumber > 0, valid = false. content is in swapfile, but this one is writevm, only need to put the data to memory
			// we donot need to content in this swapfile.
			pageTable[vpn].used = true;
			if(ppn == -1){
				handlePageFault(vpn);
			}

			if (pageTable[vpn].readOnly){
				//System.out.println("The reason is the page table is read only");
				return -1;
			}

			VMKernel.pinLock.acquire(); 

			//if ppn != -1, but it is invalid, the ppn is spn, the data is in swapfile. this one is from data to memory.
			/*Change dirty bit to 1 */
			pageTable[vpn].dirty = true;
			ppn = pageTable[vpn].ppn;
			//System.out.println("The page number in wvm is: " + ppn + " and the vpn is: " + vpn);
			
			VMKernel.pinTable[ppn] = true;

			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize-vpn_offset);

			if (physcial_address < 0 || physcial_address >= memory.length){
				//System.out.println("The reason is the pm is invalid.");
				VMKernel.pinTable[ppn] = false;
				VMKernel.pinCV.wakeAll();
				VMKernel.pinLock.release();
				return -1;
			}
			System.arraycopy(data, offset, memory, physcial_address, amount);
			//System.out.println("The offset will be: " + offset + " and in this offset, we transfer the");

			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			VMKernel.pinTable[ppn] = false;
			VMKernel.pinCV.wakeAll();
			VMKernel.pinLock.release();
			//how about the offset, very confused on this part.
		}
		//System.out.println("The transferred Bytes(write bytes): " + transferredBytes);

		return transferredBytes;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		//System.out.println("can we in the readvirtualmemory.");

		//handlepage fault, store in the swapfile, readvm, find these memory in the swapfile. OK, we read from the swapfile, 
		//valid this memory again, and put back the memory into physical memory. ask TA.

		
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
			
			pageTable[vpn].used = true;

			if(ppn == -1){
				handlePageFault(vpn);
			}
				
			ppn = pageTable[vpn].ppn;
			VMKernel.pinLock.acquire(); 
			VMKernel.pinTable[ppn] = true;

			int physcial_address = Processor.makeAddress(ppn, vpn_offset);

			amount = Math.min(length, pageSize-vpn_offset);

			if (physcial_address < 0 || physcial_address >= memory.length){
				VMKernel.pinTable[ppn] = false;
				VMKernel.pinCV.wakeAll();
				VMKernel.pinLock.release(); 
				return -1;
			}
				
			System.arraycopy(memory, physcial_address, data, offset, amount);

			
			length -= amount;
			transferredBytes += amount;
			vaddr += amount;
			offset += amount;
			
			VMKernel.pinTable[ppn] = false;
			VMKernel.pinCV.wakeAll();
			VMKernel.pinLock.release(); 
			//how about the offset, very confused on this part.
		}
		

		return transferredBytes;
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

}
