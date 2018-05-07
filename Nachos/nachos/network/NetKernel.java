package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.network.*;

/**
 * A kernel with network support.
 */
public class NetKernel extends UserKernel {
	/**
	 * Allocate a new networking kernel.
	 */
	public NetKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		postOffice = new PostOffice();
		ncc = new NetCommandCenter();
	}

	/**
	 * Test the network. Create a server thread that listens for pings on port
	 * 1 and sends replies. Then ping one or two hosts. Note that this test
	 * assumes that the network is reliable (i.e. that the network's
	 * reliability is 1.0).
	 */
	public void selfTest() {
		super.selfTest();
		
		// stalling to prepare other machines to run
		System.out.println("Press any key to start the network test...");
		console.readByte(true);
		
//		int local = Machine.networkLink().getLinkAddress();
//		
//		// send syn packet if network 0
//		if(local == 0) {
//			ncc.connect(1, 1);
//		}
//		// send synack packet if network 1 and syn packet is detected 
//		else {
//			ncc.accept(1);
//		}
//		Machine.halt();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		//super.run();
		int local = Machine.networkLink().getLinkAddress();
		
		if(local == 0) {
			NetProcess process = NetProcess.newNetProcess();

			String shellProgram = "client.coff";

			// pass arguments for coff files here!!
			String[] arguments = { "1", "15" };
			Lib.assertTrue(process.execute(shellProgram, arguments));

			KThread.currentThread().finish();
		}
//		else if(local == 1) {
//			NetProcess process = NetProcess.newNetProcess();
//
//			String shellProgram = "client.coff";
//
//			// pass arguments for coff files here!!
//			String[] arguments = { "2", "0" };
//			Lib.assertTrue(process.execute(shellProgram, arguments));
//
//			KThread.currentThread().finish();
//		}
		else {
			NetProcess process = NetProcess.newNetProcess();

			String shellProgram = "host.coff";

			// pass arguments for coff files here!!
			String[] arguments = { "15" };
			Lib.assertTrue(process.execute(shellProgram, arguments));

			KThread.currentThread().finish();
		}
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/*
	private void ping(int dstLink) {
		int srcLink = Machine.networkLink().getLinkAddress();

		System.out.println("PING " + dstLink + " from " + srcLink);

		long startTime = Machine.timer().getTime();

		MailMessage ping;

		try {
			ping = new MailMessage(dstLink, 1,
					Machine.networkLink().getLinkAddress(), 0,
					new byte[0]);
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached();
			return;
		}

		postOffice.send(ping);

		MailMessage ack = postOffice.receive(0);

		long endTime = Machine.timer().getTime();

		System.out.println("time=" + (endTime-startTime) + " ticks");	
	}

	private void pingServer() {
		while (true) {
			MailMessage ping = postOffice.receive(1);

			MailMessage ack;

			try {
				ack = new MailMessage(ping.packet.srcLink, ping.srcPort,
						ping.packet.dstLink, ping.dstPort,
						ping.contents);
			}
			catch (MalformedPacketException e) {
				// should never happen...
				continue;
			}

			postOffice.send(ack);
		}	
	}
	*/
	// variables for selfTest
	private PostOffice postOffice;
	private NetCommandCenter ncc;

	// dummy variables to make javac smarter
	private static NetProcess dummy1 = null;
}
