package nachos.network;


import java.util.HashSet;
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
		connections = new ConnectionMap();
		unackMessages = new HashSet<MailMessage>();

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
		// Get the connection state
		int connectionState = connections.getConnectionState(mail.packet.dstLink, mail.dstPort);

		// Handle specified port's all possible Connection states
		switch(connectionState) {
		case Connection.CLOSED:
			handleClosed(mail);
			break;
		case Connection.SYN_SENT:
			handleSYNSent(mail);
			break;
		case Connection.SYN_RCVD:
			handleSYNRcvd(mail);
			break;
		case Connection.ESTABLISHED:
			handleEstab(mail);
			break;
		case Connection.STP_SENT:
			handleSTPSent(mail);
			break;
		case Connection.STP_RCVD:
			handleSTPRcvd(mail);
			break;
		case Connection.CLOSING:
			handleClosing(mail);
			break;
		default:
			Lib.assertNotReached("Unsupported connection state in handleMessage(): " + connectionState);
		}
	}

	/**
	 * Handles how CLOSED connection handles the message
	 */
	private void handleClosed(MailMessage mail) {
		// Extract tag bits from mail
		int tag = extractTag(mail);

		switch(tag) {
		case SYN:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received in CLOSED");

			// Inserting to waiting list until it's established (There is a chance of SYN/ACK Packet drop). 
			Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to SYN_RCVD connections");
			connections.add(new Connection(mail, Connection.SYN_RCVD));
			break;

		case FIN:
			Lib.assertNotReached("FIN is not supported yet in handleClosed()");
			break;
		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleClosed()" + tag);
		}
	}

	/**
	 * Handles how SYN_SENT connection handles the message. Also checks for deadlock.
	 */
	private void handleSYNSent(MailMessage mail) {
		// Extract tag bits from mail
		int tag = extractTag(mail);

		switch(tag) {
		case SYN:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received in SYN_SENT");
			Lib.assertNotReached("(Network" + Machine.networkLink().getLinkAddress() + "protocol deadlock");
			break;

		case SYNACK:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYNACK packet is received in SYN_SENT");

			// Connection is confirmed. Establishing connection
			Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to ESTABLISHED connections");
			connections.switchConnection(Connection.ESTABLISHED, new Connection(mail, Connection.SYN_SENT));
			break;

		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleSYNSent()");
		}
	}

	/**
	 * Handles how SYN_RCVD connection handles the message
	 */
	private void handleSYNRcvd(MailMessage mail) {
		//Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") handleSYNRcvd doesn't do anything");
	}

	/**
	 * Handles how ESTABLISHED connection handles the message
	 */
	private void handleEstab(MailMessage mail) {
		// Extract tag bits from mail
		int tag = extractTag(mail);

		switch(tag) {
		case SYN:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received in ESTABLISHED");

			// Inserting to waiting list until it's established (There is a chance of SYN/ACK Packet drop). 
			Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to SYN_RCVD connections");
			connections.add(new Connection(mail, Connection.SYN_RCVD));
			break;
		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleEstab()");
		}
	}

	/**
	 * Handles how STP_SENT connection handles the message
	 */
	private void handleSTPSent(MailMessage mail) {
		Lib.assertNotReached("Not ready to support STP_SENT state");
	}

	/**
	 * Handles how STP_RCVD connection handles the message
	 */
	private void handleSTPRcvd(MailMessage mail) {
		Lib.assertNotReached("Not ready to support STP_RCVD state");
	}

	/**
	 * Handles how CLOSING connection handles the message
	 */
	private void handleClosing(MailMessage mail) {
		Lib.assertNotReached("Not ready to support CLOSING state");
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
		Lock lock = new Lock();
		// Find an available port and pop it out of the available port list
		lock.acquire();
		if(availPorts.isEmpty())
			return -1;
		int srcPort = availPorts.first();
		availPorts.remove(availPorts.first());
		lock.release();
		
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
			lock.acquire();
			unackMessages.add(synMail);
			lock.release();
			
			// Goto "SYN_SENT" state
			Connection connection = new Connection(
					srcLink, 
					dstLink, 
					srcPort,
					dstPort,
					Connection.SYN_SENT);
			connections.add(connection);
			
			// Keep resending until the connection is established
			Lib.debug(dbgConn, "Waiting for Connection["+connection+"]");
			while(!isEstablished(connection))
				NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
			
			// Connection is established. Removing the message from resend list
			lock.acquire();
			unackMessages.remove(synMail);
			lock.release();
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at connect()");
			// continue;
		}
		Lib.debug(dbgConn, "Connection finished");
		return srcPort;
	}

	/**
	 * Accepts a waiting connection of the particular port. Return 0 if success or -1 if failure.
	 */
	public int accept(int srcPort) {
		// Get the next waiting connection if exists
		Connection connectMe = connections.findWaitingConnection(Machine.networkLink().getLinkAddress(), srcPort);
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
			connectMe.state = Connection.ESTABLISHED;
			connections.add(connectMe);
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at accept()");
			// continue;
		}
		Lib.debug(dbgConn, "Accept finished");
		return 0;
	}

	/**
	 * Resends all packets that are yet to be recognized.
	 */
	private void resendAll() {
		while(true) {
			Lock lock = new Lock();
			lock.acquire();
			
			for(MailMessage m : unackMessages)
				send(m);
			
			lock.release();
			NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
		}
	}

	/**
	 * Check whether the given connection is established (in ESTABLISHED state)
	 */
	private boolean isEstablished(Connection findMe) {
		return Connection.ESTABLISHED == connections.getConnectionState(findMe.srcLink, findMe.srcPort);
	}


	// Packet tag bits
	private static final int SYN = 1, ACK = 2, SYNACK = 3, STP = 4, FIN = 8, FINACK = 10;

	// Packet contents index
	private static final int MBZ = 0, MBZ_TAGS = 1;

	// Connection Map
	ConnectionMap connections;

	// Other constants
	private static final int RETRANSMIT_INTERVAL = 20000;

	// Data structures
	private static final char dbgConn = 'c';
	private HashSet<MailMessage> unackMessages;
	private TreeSet<Integer> availPorts;

	// Locks and conditions
}
