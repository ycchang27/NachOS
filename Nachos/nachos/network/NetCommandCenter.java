package nachos.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeSet;

import nachos.machine.*;
import nachos.threads.*;

/**
 * PostOffice with some additional features.
 * 	- provides connect/accept protocol between two ports
 *	- provides assurance that the packet is resent upon failure during delivery
 */
public class NetCommandCenter extends PostOffice {
	public NetCommandCenter() {

		// NetCommunicator variables
		waitingConnections = new ArrayList<Connection>();
		estabConnections = new LinkedList<Connection>();
		unackMessages = new HashSet<MailMessage>();

		messageLock = new Lock();
		portLock = new Lock();
		threadLock = new Lock();
		postalLock = new Lock();
		resendLock = new Lock();
		unackMessageLock = new Lock();
		connectLock = new Lock();

		resendCond = new Condition(resendLock);
		postalCond = new Condition(postalLock);
		connectCond = new Condition(connectLock);

		availPorts = new TreeSet<Integer>();
		for (int i = 0; i < MailMessage.portLimit; i++)
			availPorts.add(i);

		// PostOffice variables
		messageReceived = new Semaphore(0);
		messageSent = new Semaphore(0);
		sendLock = new Lock();

		Runnable receiveHandler = new Runnable() {
			public void run() { receiveInterrupt(); }
		};
		Runnable sendHandler = new Runnable() {
			public void run() { sendInterrupt(); }
		};
		Machine.networkLink().setInterruptHandlers(receiveHandler,
				sendHandler);

		KThread pd = new KThread(new Runnable() {
			public void run() { postalDelivery(); }
		});
		KThread ra = new KThread(new Runnable() {
			public void run() { resendAll(); }
		});
		pd.fork();
		ra.fork();
		Lib.debug(dbgNet, "Constructor finished");
	}

	/**
	 * Modified version of PostOffice's postalDelivery(). Instead of inserting 
	 * arrived message at the queue, it calls a helper method, handlePacket(),
	 * to insert it into the correct data structure.
	 */
	protected void postalDelivery() {
		while(true) {
			// Wait until a Packet arrives at NetworkLink
			messageReceived.P();

			// A Packet is received. Checking whether the arrival was successful.
			Packet p = Machine.networkLink().receive();
			try {
				MailMessage mail = new MailMessage(p);
				Lib.debug(dbgNet, "receiving mail: " + mail);

				// The arrival was successful. Calling the helper method to handle Packet
				handleMessage(mail);
			}
			catch (MalformedPacketException e) {
				Lib.assertNotReached("Packet is null at postalDelivery()");
				// continue;
			}
		}
	}

	/**
	 * Inserts Packet into the correct data structure. Also checks for potential errors
	 * like deadlock (2 Machines sent SYN Packet to each other)
	 */
	private void handleMessage(MailMessage mail) {
		messageLock.acquire();	// entering critical section

		// Extract tag bits from mail
		int tag = extractTag(mail);

		switch(tag) {
		case SYN:
			System.out.println("(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received"); // temp test print
			Lib.debug(dbgNet, "(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received");
			// Check for network deadlock
//			if(waitingConnections.contains(new Connection(mail, SYN_SENT)))
//				Lib.assertNotReached("Network deadlock detected at handlePacket()");

			// No deadlock detected. Inserting to waiting list even if this connection has been
			// established (There is a chance of SYN/ACK Packet drop). 
			Lib.debug(dbgNet, "Inserting Connection["+new Connection(mail, SYN_RCVD)+"] to waitingConnections");
			waitingConnections.add(new Connection(mail, SYN_RCVD));
			break;

		case SYNACK:
			System.out.println("(Network" + Machine.networkLink().getLinkAddress() + ") SYNACK packet is received"); // temp test print
			Lib.debug(dbgNet, "(Network" + Machine.networkLink().getLinkAddress() + ") SYNACK packet is received");
			// Connection is confirmed. Establishing connection
			if(!hasConnection(new Connection(mail, ESTABLISHED))) {
				Lib.debug(dbgNet, "Inserting Connection["+new Connection(mail, SYN_RCVD)+"] to estabConnections");
				estabConnections.add(new Connection(mail, ESTABLISHED));
			}
			break;

		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handlePacket()");
		}

		messageLock.release();	// exiting critical section
	}

	/**
	 * Extracts tag components in Packet.
	 */
	private int extractTag(MailMessage mail) {
		byte tag = mail.contents[MBZ_TAGS];
		return tag;
	}

	/**
	 * Connects to a remote/local host. Returns the chosen available port  or -1 if error
	 * has occurred.
	 */
	public int connect(int dstLink, int dstPort) {
		portLock.acquire();	// entering critical section

		// Find an available port and pop it out of the available port list
		if(availPorts.isEmpty())
			return -1;
		int srcPort = availPorts.first();
		availPorts.remove(availPorts.first());

		portLock.release();	// exiting critical section

		try {
			// Send SYN Packet
			int srcLink = Machine.networkLink().getLinkAddress();
			byte[] contents = new byte[2];
			contents[MBZ] = 0;
			contents[MBZ_TAGS] = SYN;
			MailMessage synMail = new MailMessage(
					dstLink, 
					dstPort, 
					srcLink, 
					srcPort,
					contents);
			send(synMail);

			// Insert into resend list
			unackMessageLock.acquire();	// entering critical section
			unackMessages.add(synMail);
			unackMessageLock.release();	// exiting critical section

			// Keep resending until SYN/ACK Packet is arrived
			Connection connection = new Connection(
					srcLink, 
					dstLink, 
					srcPort,
					dstPort,
					ESTABLISHED);
			Lib.debug(dbgNet, "Waiting for Connection["+connection+"]");
			while(!hasConnection(connection)) {
				NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
				if(!estabConnections.isEmpty())
					Lib.debug(dbgNet, "Something is inside estabConnections");
			}
			// Connection is established. Removing the message from resend list
			unackMessageLock.acquire();	// entering critical section
			unackMessages.remove(synMail);
			unackMessageLock.release();	// exiting critical section
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at connect()");
			// continue;
		}
		System.out.println("Connect finished"); // temp test print
		return srcPort;
	}

	/**
	 * Accepts a waiting connection of the particular port. Return 0 if success or -1 if failure.
	 */
	public int accept(int srcPort) {
		// Get the next waiting connection if exists
		Connection connectMe = findWaitingConnection(srcPort);
		if(connectMe == null)
			return -1;

		// Send SYN/ACK Packet
		Lib.debug(dbgNet, "Accepting Connection["+connectMe+"]");
		int srcLink = Machine.networkLink().getLinkAddress();
		byte[] contents = new byte[2];
		contents[MBZ] = 0;
		contents[MBZ_TAGS] = SYNACK;
		try {
			MailMessage synackMail = new MailMessage(
					connectMe.dstLink,  
					connectMe.dstPort, 
					srcLink,
					srcPort,
					contents);
			send(synackMail);

			// Establish connection.
			messageLock.acquire();	// entering critical section
			estabConnections.add(connectMe);
			messageLock.release();	// exiting critical section
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at accept()");
			// continue;
		}
		Lib.debug(dbgNet, "Accept finished");
		System.out.println("Accept finished"); // temp test print
		return 0;
	}

	/**
	 * Find a port's waiting connection if exists. Returns null if not found
	 */
	private Connection findWaitingConnection(int srcPort) {
		// Find a connection that matches srcPort
		for(Connection c : waitingConnections) {
			if(c.srcPort == srcPort) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Resends all packets that are yet to be recognized.
	 */
	private void resendAll() {
		while(true) {
			for(MailMessage m : unackMessages)
				send(m);
			NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
		}
	}
	
	/**
	 * Check whether the given connection exists in estabConnections
	 */
	private boolean hasConnection(Connection findMe) {
		for(Connection c : estabConnections) {
			if(c.equals(findMe)) {
				return true;
			}
		}
		return false;
	}

	// Helper class
	/**
	 * Contains all connection related info, including addresses, ports, and its status
	 */
	private class Connection {
		public int srcLink, dstLink;
		public int srcPort, dstPort;
		public int status;

		public Connection(int srcLink, int dstLink, int srcPort, int dstPort, int status) {
			this.srcLink = srcLink;
			this.dstLink = dstLink;
			this.srcPort = srcPort;
			this.dstPort = dstPort;
			this.status = status;
		}

		/**
		 * Takes in mail as the main argument. mail is assumed to be the message that
		 * was received, not sent, so Links and Ports are reversed.
		 */
		public Connection(MailMessage mail, int status) {
			srcLink = mail.packet.dstLink; 
			dstLink = mail.packet.srcLink;
			srcPort = mail.dstPort;
			dstPort = mail.srcPort;
			this.status = status;
		}
		
		@Override
		public boolean equals(Object o) {
			Connection c = (Connection) o;
			return (srcLink == c.srcLink && dstLink == c.dstLink && srcPort == c.srcPort
					&& dstPort == c.dstPort) || (srcLink == c.dstLink && dstLink == c.srcLink
					&& srcPort == c.dstPort && dstPort == c.srcPort);
		}
		
		@Override
		public String toString() {
			return "("+srcLink+","+srcPort+") -> ("+dstLink+","+dstPort+")";
		}
		
	}

	// Packet tag bits
	private static final int SYN = 1, SYNACK = 3;

	// Packet contents index
	private static final int MBZ = 0, MBZ_TAGS = 1;

	// Connection state
	private static final int SYN_SENT = 0, SYN_RCVD = 1, ESTABLISHED = 2;

	// Other constants
	private static final int RETRANSMIT_INTERVAL = 2000000;

	// Data structures
	private ArrayList<Connection> waitingConnections;
	private LinkedList<Connection> estabConnections;
	private HashSet<MailMessage> unackMessages;
	private TreeSet<Integer> availPorts;

	// Locks and conditions
	private Lock messageLock, portLock, threadLock, unackMessageLock, connectLock, postalLock, resendLock;
	private Condition connectCond;
	private Condition postalCond;
	private Condition resendCond;
}
