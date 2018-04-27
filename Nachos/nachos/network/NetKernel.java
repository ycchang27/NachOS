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

//		KThread serverThread = new KThread(new Runnable() {
//			public void run() { pingServer(); }
//		});
//
//		serverThread.fork();
//
//		System.out.println("Press any key to start the network test...");
//		console.readByte(true);
//
//		int local = Machine.networkLink().getLinkAddress();
//
//		// ping this machine first
//		ping(local);
//
//		// if we're 0 or 1, ping the opposite
//		if (local <= 1)
//			ping(1-local);
		
//		int hostID = Machine.networkLink().getLinkAddress();
//		NetCommandCenter ncc = new NetCommandCenter();
//		System.out.println("Press any key to start the network test...");
//		console.readByte(true);
//		if(hostID == 0)
//			ncc.connect(0, 1);
//		else {
//			while(true)ncc.accept(0);
//		}
		
		// stalling to prepare other machines to run
		System.out.println("Press any key to start the network test...");
		console.readByte(true);
		
		int local = Machine.networkLink().getLinkAddress();
		
		// send syn packet if network 0
		if(local == 0) {
//			MailMessage synMail;
//			try {
//				byte[] contents = new byte[2];
//				contents[0] = 0;
//				contents[1] = 1;
//				synMail = new MailMessage(
//						1, 
//						0, 
//						local, 
//						0,
//						contents);
//			}
//			catch (MalformedPacketException e) {
//				Lib.assertNotReached();
//				return;
//			}
//
//			postOffice.send(synMail);
			ncc.connect(1, 1);
		}
		// send synack packet if network 1 and syn packet is detected 
		else {
			ncc.accept(1);
		}
		Machine.halt();
	}

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
		super.terminate();
	}

	// variables for selfTest
	private PostOffice postOffice;
	private NetCommandCenter ncc;

	// dummy variables to make javac smarter
	private static NetProcess dummy1 = null;
}
