package nachos.network;

/**
 * Contains all connection related info, including addresses, ports, and its state
 */
public class Connection {
	public int srcLink, dstLink;
	public int srcPort, dstPort;
	public int state;
	
	// Connection states
	public static final int CLOSED = -1, SYN_SENT = 0, SYN_RCVD = 1,
			ESTABLISHED = 2, STP_SENT = 3, STP_RCVD = 4, CLOSING = 5;

	public Connection(int srcLink, int dstLink, int srcPort, int dstPort, int state) {
		this.srcLink = srcLink;
		this.dstLink = dstLink;
		this.srcPort = srcPort;
		this.dstPort = dstPort;
		this.state = state;
	}

	/**
	 * Takes in mail as the main argument. mail is assumed to be the message that
	 * was received, not sent, so Links and Ports are reversed.
	 */
	public Connection(MailMessage mail, int state) {
		srcLink = mail.packet.dstLink; 
		dstLink = mail.packet.srcLink;
		srcPort = mail.dstPort;
		dstPort = mail.srcPort;
		this.state = state;
	}
	
	/**
	 * Determine if the current state is valid.
	 * 
	 * @return true if valid, false otherwise
	 */
	public boolean validState() {
		return state == CLOSED || state == SYN_SENT || state == SYN_RCVD ||
				state == ESTABLISHED || state == STP_SENT || state == STP_RCVD ||
				state == CLOSING;
	}
	
	/**
	 * Determine if the given state is valid.
	 * 
	 * @return true if valid, false otherwise
	 */
	public static boolean validState(int state) {
		return state == CLOSED || state == SYN_SENT || state == SYN_RCVD ||
				state == ESTABLISHED || state == STP_SENT || state == STP_RCVD ||
				state == CLOSING;
	}

	@Override
	/**
	 * Search helper function
	 */
	public boolean equals(Object o) {
		Connection c = (Connection) o;
		return (srcLink == c.srcLink && dstLink == c.dstLink && srcPort == c.srcPort
				&& dstPort == c.dstPort);// || (srcLink == c.dstLink && dstLink == c.srcLink
				//&& srcPort == c.dstPort && dstPort == c.srcPort);
	}

	@Override
	/**
	 * Debug print helper
	 */
	public String toString() {
		return "("+srcLink+","+srcPort+") -> ("+dstLink+","+dstPort+")";
	}

}
