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
		Lock lock = new Lock();
		
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
			//Lib.debug(dbgConn, "(Network" + Machine.networkLink().getLinkAddress() + ") DATA packet is received in ESTABLISHED");
			
			// Add to the waiting data message list
			//Lib.debug(dbgConn, "Inserting contents at (" + Machine.networkLink().getLinkAddress() + ", " + mail.dstPort + ")");
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
	 * Connects to a remote/local host. Returns the corresponding connection
	 * or null if error occurs
	 */
	public Connection connect(int dstLink, int dstPort) {
		Lock lock = new Lock();
		// Find an available port and pop it out of the available port list
		lock.acquire();
		if(availPorts.isEmpty())
			return null;
		int srcPort = 1;// availPorts.first();
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
			
//			System.out.println("Connection finished");
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
	 * Accepts a waiting connection of the particular port. Return 0 if success or -1 if failure.
	 */
	public Connection accept(int srcPort) {
		// Get the next waiting connection if exists
		Connection connectMe = connections.findWaitingConnection(Machine.networkLink().getLinkAddress(), srcPort);
		if(connectMe == null)
			return null;

		// Send SYN/ACK Packet
		Lib.debug(dbgConn, "Accepting Connection["+connectMe+"]");
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
			
			Lib.debug(dbgConn, "Accepted Connection["+connectMe+"]");
			return connectMe;
		}
		catch (MalformedPacketException e) {
			Lib.assertNotReached("Packet is null at accept()");
			// continue;
		}
		return null;
	}
	
	/**
	 * Sends data packet(s) depending on the size
	 * 
	 * @param c - current connection
	 * @param contents - bytes to send
	 * @param size - size of the contents
	 * @param bytesSent - bytes that have been sent so far
	 * @return updated bytesSent
	 */
	public int sendData(Connection c, byte[] contents, int size, int bytesSent) {
		Lib.assertTrue(size >= 0);
		
		// Keep sending until all bytes are sent
		Lock lock = new Lock();
		int i = 0, length, contentSize = CONTENTS, offset=0;
		
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
		
		return bytesSent + offset;
//		if(size > contentSize) {
//			for(i = 0; i*contentSize < size; i ++) {
//				// Setup content array
//				length = contentSize;
//				byte[] dataToSend = new byte[length];
//				System.arraycopy(contents, i, dataToSend, 0, length);
//				
//				// Generate sequence number
//				offset += length;
//				byte[] sendMe = createData(offset+bytesSent, dataToSend);
//				
//				// Send the packet
//				try {
//					MailMessage dataMessage = new MailMessage(
//							c.dstLink,  
//							c.dstPort, 
//							c.srcLink,
//							c.srcPort,
//							sendMe);
//					send(dataMessage);
//					
//					// Also insert into the resendList
//					lock.acquire();
//					unackMessages.add(dataMessage);
//					lock.release();
//				}
//				catch(MalformedPacketException e) {
//					Lib.assertNotReached("MailMessage is failed at sendData()");
//				}
//			}
//			if(size - i*contentSize > 0) {
//				// Setup content array
//				length = size - i*contentSize;
//				byte[] dataToSend = new byte[length];
//				System.arraycopy(contents, i, dataToSend, 0, length);
//				
//				// Generate sequence number
//				offset += length;
//				byte[] sendMe = createData(offset+bytesSent, dataToSend);
//
//				// Send the packet
//				try {
//					MailMessage dataMessage = new MailMessage(
//							c.dstLink,  
//							c.dstPort, 
//							c.srcLink,
//							c.srcPort,
//							sendMe);
//					send(dataMessage);
//					
//					// Also insert into the resendList
//					lock.acquire();
//					unackMessages.add(dataMessage);
//					lock.release();
//				}
//				catch(MalformedPacketException e) {
//					Lib.assertNotReached("MailMessage is failed at sendData()");
//				}
//			}
//		}
//		else {
//			// Setup content array
//			length = size;
//			byte[] dataToSend = new byte[length];
//			System.arraycopy(contents, i, dataToSend, 0, length);
//			
//			// Generate sequence number
//			offset += length;
//			byte[] sendMe = createData(offset+bytesSent, dataToSend);
//
//			// Send the packet
//			try {
//				MailMessage dataMessage = new MailMessage(
//						c.dstLink,  
//						c.dstPort, 
//						c.srcLink,
//						c.srcPort,
//						sendMe);
//				send(dataMessage);
//				
//				// Also insert into the resendList
//				lock.acquire();
//				unackMessages.add(dataMessage);
//				lock.release();
//			}
//			catch(MalformedPacketException e) {
//				Lib.assertNotReached("MailMessage is failed at sendData()");
//			}
//		}
		
//		return bytesSent + offset;
	}
	
	/**
	 * Receives a data packet
	 */
	public byte[] receiveData(Connection c) {
		if(!waitingDataMessages[c.srcPort].isEmpty()) {
			//System.out.println("Something is inside at " + c.dstPort);
		}
		return (waitingDataMessages[c.srcPort].isEmpty()) ? null : waitingDataMessages[c.srcPort].removeFirst().contents;
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
		return Connection.ESTABLISHED == connections.getConnectionState(findMe.dstLink, findMe.dstPort, findMe.srcLink, findMe.srcPort);
	}
	
	/**
	 * Create a content array that contains both sequence number and data
	 */
	public byte[] createData(int seq, byte[] data) {
		Lib.assertTrue(seq >= 0 && data.length <= CONTENTS);
		
		// Create a content array
		byte[] contents = new byte[HEADERS+SEQNUM+data.length];
		contents[MBZ] = 0;
		contents[MBZ_TAGS] = DATA;
		
		// Insert sequence number
		System.arraycopy(ByteBuffer.allocate(SEQNUM).putInt(seq).array(), 0, contents, HEADERS, SEQNUM);
		
		// Insert data
		System.arraycopy(data, 0, contents, HEADERS+SEQNUM, data.length);
		
//		// test
//		System.out.println("data: " + new String(data));
//		System.out.println("Contents: " + new String(contents));
//		byte[] temp = new byte[SEQNUM];
//		System.arraycopy(contents, HEADERS, temp, 0, SEQNUM);
//		System.out.println("seq #: " + ByteBuffer.wrap(temp).order(ByteOrder.BIG_ENDIAN).getInt());
		
		return contents;
	}
	
	/**
	 * Extract sequence number from mail message (DATA only)
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
	 * Extract buffer from MailMessage content array (DATA only)
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
