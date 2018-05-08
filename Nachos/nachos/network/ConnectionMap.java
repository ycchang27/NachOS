package nachos.network;

import nachos.threads.*;
import nachos.machine.*;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Synchronized Map of <ConnectionState, Connections> where ConnectionState represents constant 
 * variables in Connection (CLOSED, SYN_SENT, etc...) and Connections represent a list of 
 * Connections (currently using LinkedList). Synchronization feature was inspired by SynchList
 * in threads package. Some parts of code may be redundant in order to be consistent with
 * synchronization. Some algorithms can be optimized, but didn't due to lack of time.
 * 
 * Each Connection is unique throughout the map, so no 2 ConnectionStates should contain the same
 * Connection. This also means a certain port has no multiple connections.
 */
public class ConnectionMap {
	/**
	 * Allocate a new synchronized map
	 */
	public ConnectionMap() {
		map = new HashMap<Integer, LinkedList<Connection>>();
	}
	
	/**
	 * Add the specified connection to the map.
	 * 
	 * @param	c	the connection to add. Must not be <tt>null</tt>.
	 */
	public void add(Connection c) {
		Lock lock = new Lock();
		Lib.assertTrue(c != null && c.validState());

		lock.acquire();
		
		// Get the connection state and its corresponding list
		LinkedList<Connection> connections;
		Integer connectionState = c.state;
		if(map.containsKey(connectionState)) {
			connections = map.get(connectionState);
			map.remove(connectionState);	// temporary removal for replacement
		}
		else
			connections = new LinkedList<Connection>();
		
		// Check for duplicates (if it exists, then do nothing and reinsert)
		if(!connections.isEmpty()) {
			for(Connection con : connections) {
				if(con.equals(c)) {
					// Insert into the list
					connections.add(c);
					
					// Insert (back) into the map
					map.put(connectionState, connections);
					
					lock.release();
					return;
				}
			}
		}
		
		// Duplicate not found. Insert into the list
		connections.add(c);
		
		// Insert (back) into the map
		map.put(connectionState, connections);
		
		lock.release();
	}
	
	/**
     * Close (remove) a connection in the closing state, no blocking
     * involved. Returns null if c is not found.
     *
     * @param	c	the connection to close. Must not be <tt>null</tt>.
     * @return	the element removed from the map.
     */
    public Connection close(Connection c) {
    	Lock lock = new Lock();
    	Lib.assertTrue(c != null && c.state == Connection.CLOSING);
    	
    	lock.acquire();
    	
    	// Check whether there are any CLOSING connections
    	if(!map.containsKey(Connection.CLOSING)) {
    		lock.release();
    		return null;	// no CLOSING connections
    	}
    	
    	// Get the CLOSING connections and remove the connection if exists
    	LinkedList<Connection> connections = map.get(Connection.CLOSING);
    	Integer connectionState = c.state;
    	for(Connection con : connections) {
			if(con.equals(c)) {
				// Remove the connection from the list
				connections.remove(c);
				
				// Insert (back) into the map
				map.put(connectionState, connections);
				
				lock.release();
				return c;
			}
		}
		
    	// Connection doesn't exist. Reinsert (back) into the map and return null
		map.put(connectionState, connections);
    	
    	lock.release();
		return null;
    }
    
    /**
     * Switch the existing connection to different state. c must exist in the map.
     * 
     * @param	newState	new state for the connection
     * @param	c			connection that wants to move. Must not be <tt>null</tt>.
     * @return	true if successful, false otherwise
     */
    public boolean switchConnection(int newState, Connection c) {
    	Lock lock = new Lock();
    	Lib.assertTrue(c != null && c.validState() && Connection.validState(newState));
    	
    	lock.acquire();
    	
    	// Get the original connection state and its corresponding list
    	LinkedList<Connection> originalConns;
    	Integer originalConnState = c.state;
    	if(map.containsKey(originalConnState)) {
			originalConns = map.get(originalConnState);
			map.remove(originalConnState);	// temporary removal for replacement
		}
		else
			originalConns = new LinkedList<Connection>();
    	
    	// Get the new connection state and its corresponding list
    	LinkedList<Connection> newConns;
    	Integer newConnState = newState;
    	if(map.containsKey(newConnState)) {
			newConns = map.get(newConnState);
			map.remove(newConnState);	// temporary removal for replacement
		}
		else
			newConns = new LinkedList<Connection>();
    	
    	// Remove the connection in the original list and transfer it to new list
    	for(Connection con : originalConns) {
			if(con.equals(c)) {
				// Remove connection from the original list
				originalConns.remove(c);
				
				// Insert (back) into the map
				map.put(originalConnState, originalConns);
				
				// Insert connection to the new list
				newConns.add(c);
				
				// Insert (back) into the map
				map.put(newConnState, newConns);
				
				lock.release();
				return true;
			}
		}
    	
    	// Connection doesn't exist in original list. Reinsert both original 
    	// and new list back to where they belong
		map.put(originalConnState, originalConns);
		map.put(newConnState, newConns);
    	
    	lock.release();
    	
    	return false;
    }
    
    /**
     * Get the connection state with given parameters.
     * 
     * @param	srcLink	source address
     * @param	srcPort	source port
     * @return	state of the connection (CLOSED if not found)
     */
    public int getConnectionState(int dstLink, int dstPort, int srcLink, int srcPort) {
    	Lock lock = new Lock();
    	lock.acquire();
    	
    	// Search through all connection states
    	for(Integer connectionState : map.keySet())
    		// Search through all connections
    		for(Connection c : map.get(connectionState))
    			// Return the connection state if the connection is found
    			if(c.srcLink == srcLink && c.srcPort == srcPort && c.dstLink == dstLink && c.dstPort == dstPort)
    				return connectionState;
    	
    	// Connection doesn't exist. Conclude as closed
    	lock.release();
    	return Connection.CLOSED;
    }
    
    /**
     * Find a port's waiting connection if exists. Returns null if not found
     * 
     * @param	srcLink	source address
     * @param	srcPort soruce port
     * @return	corresponding connection (null if not found)
     */
    public Connection findWaitingConnection(int srcLink, int srcPort) {
		// Find a connection that matches srcLink and srcPort
    	if(map.get(Connection.SYN_RCVD) != null && !map.get(Connection.SYN_RCVD).isEmpty()) {
			for(Connection c : map.get(Connection.SYN_RCVD)) {
				if(c.srcLink == srcLink && c.srcPort == srcPort) {
					// Remove from the list
					LinkedList<Connection> connections = map.remove(Connection.SYN_RCVD);
					connections.remove(c);
					
					// Reinsert to the map
					map.put(Connection.SYN_RCVD, connections);
					return c;
				}
			}
    	}
		return null;
	}
	
	private HashMap<Integer, LinkedList<Connection>> map;
}