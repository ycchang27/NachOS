package nachos.network;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Deque;
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
		connections = new ConnectionMap();
		unackMessages = new HashSet<MailMessage>();
		waitingDataMessages = new Deque[MailMessage.portLimit];
		availPorts = new TreeSet<Integer>();
		for (int i = 0; i < MailMessage.portLimit; i++) {
			availPorts.add(i);
			waitingDataMessages[i] = new LinkedList<MailMessage>();
		}
		KThread ra = new KThread(new Runnable() {
			public void run() { resendAll(); }
		});
		
		
		// PostOffice variables (Reinitializing variables)
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
		
		// This automates postalDelivery(), which handles MailMessage receiving
		// procedures, and resendAll(), which handles resending unACKed MailMessages
		ra.fork();
		pd.fork();
		Lib.debug(dbgNet, "Constructor finished");
	}

	/**
	 * Modified version of PostOffice's postalDelivery(). Instead of inserting 
	 * arrived message at the SynchList, it calls a helper method, handlePacket(),
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

				// The arrival was successful. Calling the helper method to handle MailMessage
				handleMessage(mail);
			}
			catch (MalformedPacketException e) {
				Lib.assertNotReached("Packet is null at postalDelivery()");
				// continue;
			}
		}
	}

	/**
	 * Look for the MailMessage's corresponding Connection state and calls the relevant function.
	 * If no Connection found, then it's treated as CLOSED.
	 */
	private void handleMessage(MailMessage mail) {
		// Get the connection state
		int connectionState = connections.getConnectionState(mail.packet.srcLink, mail.srcPort, mail.packet.dstLink, mail.dstPort);

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
			// Insert a new Connection(SYN_RCVD or waiting state)
			Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to SYN_RCVD connections");
			connections.add(new Connection(mail, Connection.SYN_RCVD));
			break;
		case FIN:
			Lib.assertNotReached("FIN is not supported yet in handleClosed()");	// FIN is not implemented yet
			break;
		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleClosed()" + tag);	// protocol error
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
			// protocol error
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYN packet is received in SYN_SENT");
			Lib.assertNotReached("(Network" + Machine.networkLink().getLinkAddress() + "protocol deadlock");
			break;

		case SYNACK:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") SYNACK packet is received in SYN_SENT");
			// Connection is confirmed. Change SYN_SENT state to ESTABLISHED (this will stop the "blocking" state for connect
			// function
			Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to ESTABLISHED connections");
			connections.switchConnection(Connection.ESTABLISHED, new Connection(mail, Connection.SYN_SENT));
			break;
		
			// Do nothing or print error for the following
		case DATA:
			break;
			
		case STP:
			break;
			
		case FIN:
			break;
		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleSYNSent()");	// protocol error
		}
	}

	/**
	 * Handles how SYN_RCVD connection handles the message
	 */
	private void handleSYNRcvd(MailMessage mail) {
		// Do nothing
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

			// Insert to waiting list until it's established (There is a chance of SYN/ACK Packet drop). 
			if(!isEstablished(new Connection(mail, Connection.SYN_RCVD))) {
				Lib.debug(dbgConn, "Inserting Connection["+new Connection(mail, Connection.SYN_RCVD)+"] to SYN_RCVD connections");
				connections.add(new Connection(mail, Connection.SYN_RCVD));
			}
			else {
				Lib.debug(dbgConn, "Connection["+new Connection(mail, Connection.SYN_RCVD)+"] already exists");
			}
			break;
		case DATA:
			// Add to the waiting data message list (this will be used when NetProcess is trying to read)
			waitingDataMessages[mail.dstPort].add(mail);
			mail.contents[MBZ_TAGS] = ACK;
			
			// Send ACK Packet
			try {
				MailMessage ack = new MailMessage(
						mail.packet.srcLink,
						mail.srcPort,
						mail.packet.dstLink,
						mail.dstPort,
						mail.contents);
				send(ack);
			}
			catch(MalformedPacketException e) {
				// continue;
			}
			break;
			
		case ACK:
			Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") ACK packet is received in ESTABLISHED");
			
			// Remove the mail from the resend list (or "shifting" if window protocol was implemented)
			mail.contents[MBZ_TAGS] = DATA;
			try {
				unackMessages.remove(new MailMessage(mail.packet.srcLink, mail.srcPort, mail.packet.dstLink, mail.dstPort, mail.contents));
			}
			catch(MalformedPacketException e) {
				// continue;
			}
			break;
		default:
			Lib.assertNotReached("Unsupported invalid Packet tag bits in handleEstab()");
		}
	}

	/**
	 * Handles how STP_SENT connection handles the message
	 */
	private void handleSTPSent(MailMessage mail) {
		// not supported yet
		Lib.assertNotReached("Not ready to support STP_SENT state");
	}

	/**
	 * Handles how STP_RCVD connection handles the message
	 */
	private void handleSTPRcvd(MailMessage mail) {
		// not supported yet
		Lib.assertNotReached("Not ready to support STP_RCVD state");
	}

	/**
	 * Handles how CLOSING connection handles the message
	 */
	private void handleClosing(MailMessage mail) {
		Lib.assertNotReached("Not ready to support CLOSING state");
	}


	/**
	 * Extracts tag components in MailMessage.
	 */
	private int extractTag(MailMessage mail) {
		return mail.contents[MBZ_TAGS];
	}

	/**
	 * Connects to a remote/local host. Returns the corresponding connection
	 * or null if error occurs
	 */
	public Connection connect(int dstLink, int dstPort) {
		// Find an available port and pop it out of the available port list
		if(availPorts.isEmpty())
			return null;
		
		// source port
		int srcPort = availPorts.first();
		availPorts.remove(availPorts.first());
		
		try {
			// source address
			int srcLink = Machine.networkLink().getLinkAddress();
			
			// Tag specifications
			byte[] contents = new byte[2];
			contents[MBZ] = 0;
			contents[MBZ_TAGS] = SYN;
			
			// Prepare MailMessage
			MailMessage synMail = new MailMessage(
					dstLink, 
					dstPort, 
					srcLink, 
					srcPort,
					contents);
			
			// Send SYN MailMessage
			send(synMail);

			// Insert SYN MailMessage into resend list
			unackMessages.add(synMail);
			
			// Goto "SYN_SENT" state
			Connection connection = new Connection(
					srcLink, 
					dstLink, 
					srcPort,
					dstPort,
					Connection.SYN_SENT);
			
			// Insert the Connection to the map
			connections.add(connection);
			
			Lib.debug(dbgConn, "Waiting for Connection["+connection+"]");
			// Wait until the connection is established (Triggered in SYN_SENT's SYNACK)
			while(!isEstablished(connection))
				NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
			
			// Connection is established. Removing the message from resend list
			unackMessages.remove(synMail);
			
			// Return the established Connection
			Lib.debug(dbgConn, "Connection["+connection+"] finished");
			connection.state = Connection.ESTABLISHED;
			return connection;
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at connect()");
			// continue;
		}
		return null;
	}

	/**
	 * Accepts a waiting connection of the particular port. Returns the corresponding connection
	 * or null if error occurs
	 */
	public Connection accept(int srcPort) {
		// Get the next waiting Connection (or SYN_RCVD Connection) if exists
		Connection connectMe = connections.findWaitingConnection(Machine.networkLink().getLinkAddress(), srcPort);
		
		// Return null if not found
		if(connectMe == null)
			return null;

		Lib.debug(dbgConn, "Accepting Connection["+connectMe+"]");
		
		// Source address
		int srcLink = Machine.networkLink().getLinkAddress();
		
		// Tag specifications
		byte[] contents = new byte[2];
		contents[MBZ] = 0;
		contents[MBZ_TAGS] = SYNACK;
		try {
			// Prepare MailMessage
			MailMessage synackMail = new MailMessage(
					connectMe.dstLink,  
					connectMe.dstPort, 
					srcLink,
					srcPort,
					contents);
			
			// Send SYN/ACK Packet
			send(synackMail);

			// Establish connection and insert into the map
			connectMe.state = Connection.ESTABLISHED;
			connections.add(connectMe);
			
			// Return the Connection
			Lib.debug(dbgConn, "Accepted Connection["+connectMe+"]");
			return connectMe;
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at accept()");
			// continue;
		}
		
		// Accept failed
		return null;
	}
	
	/**
	 * Sends data packet(s) depending on the size. For now, it sends only 1 packet,
	 * so this only sends up to the contents.length
	 * 
	 * @param c - current connection
	 * @param contents - bytes to send
	 * @param size - size of the contents
	 * @param bytesSent - bytes that have been sent so far
	 * @return updated bytesSent (not updated for the current version)
	 */
	public int sendData(Connection c, byte[] contents, int size, int bytesSent) {
		Lib.assertTrue(size >= 0);
		
		// Keep sending until all bytes are sent
		Lock lock = new Lock();
		
		// Send the packet
		try {
			byte[] sendMe = createData(size, contents);
			MailMessage dataMessage = new MailMessage(
					c.dstLink,  
					c.dstPort, 
					c.srcLink,
					c.srcPort,
					sendMe);
			send(dataMessage);
			
			// Also insert into the resendList
			lock.acquire();
			unackMessages.add(dataMessage);
			lock.release();
		}
		catch(MalformedPacketException e) {
			Lib.assertNotReached("MailMessage is failed at sendData()");
		}
		
		return bytesSent;
	}
	
	/**
	 * Receives a data MailMessage's byte contents if exists. Returns null if not.
	 */
	public byte[] receiveData(Connection c) {
		return (waitingDataMessages[c.srcPort].isEmpty()) ? null : waitingDataMessages[c.srcPort].removeFirst().contents;
	}

	/**
	 * Resends all packets that are yet to be ACKed. This function will be keep running
	 * in a thread
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
	 * Check whether the given connection is established (in ESTABLISHED state) in the map
	 */
	private boolean isEstablished(Connection findMe) {
		return Connection.ESTABLISHED == connections.getConnectionState(findMe.dstLink, findMe.dstPort, findMe.srcLink, findMe.srcPort);
	}
	
	/**
	 * Create a content array that contains both sequence number and data
	 */
	public byte[] createData(int seq, byte[] data) {
		Lib.assertTrue(seq >= 0 && data.length <= CONTENTS);
		
		// Create a content array
		byte[] contents = new byte[HEADERS+SEQNUM+data.length];
		
		// tag specifications
		contents[MBZ] = 0;
		contents[MBZ_TAGS] = DATA;
		
		// Insert sequence number
		System.arraycopy(ByteBuffer.allocate(SEQNUM).putInt(seq).array(), 0, contents, HEADERS, SEQNUM);
		
		// Insert data
		System.arraycopy(data, 0, contents, HEADERS+SEQNUM, data.length);
		
		return contents;
	}
	
	/**
	 * Extract sequence number from MailMessage (DATA MailMessage only)
	 */
	public int extractSeq(MailMessage mail) {
		byte[] seq = new byte[SEQNUM];
		System.arraycopy(mail.contents, HEADERS, seq, 0, SEQNUM);
		Lib.debug(dbgConn, "Mail["+mail+"] has sequence number of " + ByteBuffer.wrap(seq).order(ByteOrder.BIG_ENDIAN).getInt());
		return ByteBuffer.wrap(seq).order(ByteOrder.BIG_ENDIAN).getInt();
	}
	
	/**
	 * Extract sequence number from MailMessage content array (DATA only)
	 */
	public int extractSeq(byte[] contents) {
		byte[] seq = new byte[SEQNUM];
		System.arraycopy(contents, HEADERS, seq, 0, SEQNUM);
		return ByteBuffer.wrap(seq).order(ByteOrder.BIG_ENDIAN).getInt();
	}
	
	/**
	 * Extract byte contents from MailMessage content array (DATA only)
	 */
	public byte[] extractBuffer(byte[] contents) {
		if(contents == null)
			return null;
		byte[] buffer = new byte[contents.length-HEADERS-SEQNUM];
		System.arraycopy(contents, HEADERS+SEQNUM, buffer, 0, buffer.length);
		return buffer;
	}

	// MailMessage tag bits
	private static final int DATA = 0, SYN = 1, ACK = 2, SYNACK = 3, STP = 4, FIN = 8, FINACK = 10;

	// MailMessage contents index
	private static final int MBZ = 0, MBZ_TAGS = 1;
	
	// MailMessage contents headers [MBZ][MBZ_TAG][SEQNUM][CONTENTS]
	private static final int HEADERS = 2, SEQNUM = 4, CONTENTS = MailMessage.maxContentsLength - HEADERS - SEQNUM;

	// Connection Map
	ConnectionMap connections;

	// Other constants
	private static final int RETRANSMIT_INTERVAL = 20000;

	// Data structures
	private static final char dbgConn = 'c';
	private HashSet<MailMessage> unackMessages;
	private TreeSet<Integer> availPorts;
	private Deque<MailMessage>[] waitingDataMessages;

	// Locks and conditions
}
