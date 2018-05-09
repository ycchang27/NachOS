package nachos.network;

import nachos.threads.*;
import nachos.machine.*;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Map of <ConnectionState, Connections> where ConnectionState represents constant 
 * variables in Connection (CLOSED, SYN_SENT, etc...) and Connections represent a list of 
 * Connections (currently using LinkedList). Unfortunately, this does not guarantee 
 * synchronization as there are unknown sync bugs to fix.
 * 
 * Each Connection is unique throughout the map, so no multiple same Connection would exist 
 * in the map.
 */
public class ConnectionMap {
	/**
	 * Allocate a new map
	 */
	public ConnectionMap() {
		map = new HashMap<Integer, LinkedList<Connection>>();
	}
	
	/**
	 * Add the specified connection to the map only if the connection doesn't exist in the map.
	 * 
	 * @param	c	the connection to add. Must not be <tt>null</tt>.
	 */
	public void add(Connection c) {
		Lib.assertTrue(c != null && c.validState());
		
		// Get the connection state
		Integer connectionState = c.state;
		
		// Get the corresponding LinkedList
		LinkedList<Connection> connections;
		// Remove the LinkedList from the map if it exists
		if(map.containsKey(connectionState)) {
			connections = map.get(connectionState);
			map.remove(connectionState);	// temporary removal for replacement
		}
		// Create a new LinkedList if it doesn't exist
		else
			connections = new LinkedList<Connection>();
		
		// Check for duplicates (if it exists, then do nothing and reinsert)
		if(!connections.isEmpty()) {
			for(Connection con : connections) {
				// Duplicate found. Insert the list back to the map
				if(con.equals(c)) {
					// Insert the list back to the map
					map.put(connectionState, connections);
					return;
				}
			}
		}
		
		// Duplicate not found. Insert Connection into the list
		connections.add(c);
		
		// Insert the list back to the map
		map.put(connectionState, connections);
	}
	
	/**
     * Close (remove) a connection in the CLOSING state. Returns null if c is not found.
     *
     * @param	c	the connection to close. Must not be <tt>null</tt>.
     * @return	the element removed from the map.
     */
    public Connection close(Connection c) {
    	Lib.assertTrue(c != null && c.state == Connection.CLOSING);
    	
    	// Return null if there are no CLOSING connections
    	if(!map.containsKey(Connection.CLOSING)) {
    		return null;
    	}
    	
    	// Get the CLOSING Connection list
    	LinkedList<Connection> connections = map.get(Connection.CLOSING);
    	Integer connectionState = c.state;
    	
    	// Remove the connection if exists
    	for(Connection con : connections) {
			if(con.equals(c)) {
				// Remove the connection from the list
				connections.remove(c);
				
				// Insert (back) into the map
				map.put(connectionState, connections);
				return c;
			}
		}
		
    	// Connection doesn't exist. Reinsert (back) into the map and return null
		map.put(connectionState, connections);

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
    	Lib.assertTrue(c != null && c.validState() && Connection.validState(newState));
    	
    	// Get the original connection state
    	Integer originalConnState = c.state;
    	
    	// Get the original state's LinkedList
    	LinkedList<Connection> originalConns;
    	// Remove the LinkedList from the map if it exists
    	if(map.containsKey(originalConnState)) {
			originalConns = map.get(originalConnState);
			map.remove(originalConnState);	// temporary removal for replacement
		}
    	// Create a new LinkedList if it doesn't exist
		else
			originalConns = new LinkedList<Connection>();
    	
    	// Get the new connection state
    	Integer newConnState = newState;
    	
    	// Get the new state's LinkedList
    	LinkedList<Connection> newConns;
    	// Remove the LinkedList from the map if it exists
    	if(map.containsKey(newConnState)) {
			newConns = map.get(newConnState);
			map.remove(newConnState);	// temporary removal for replacement
		}
    	// Create a new LinkedList if it doesn't exist
		else
			newConns = new LinkedList<Connection>();
    	
    	// Find the connection in the original list
    	for(Connection con : originalConns) {
			// Connection found. Remove it from the original list and insert into
    		// new list
    		if(con.equals(c)) {
				// Remove connection from the original list
				originalConns.remove(c);
				
				// Insert the original list back to the map
				map.put(originalConnState, originalConns);
				
				// Insert Connection to the new list
				newConns.add(c);
				
				// Insert the new list back into the map
				map.put(newConnState, newConns);
				return true;
			}
		}
    	
    	// Connection doesn't exist in original list. Reinsert both original 
    	// and new list back to where they belong
		map.put(originalConnState, originalConns);
		map.put(newConnState, newConns);
    	return false;
    }
    
    /**
     * Get the connection state with given parameters. Returns CLOSED if not found.
     * 
     * @param	srcLink	source address
     * @param	srcPort	source port
     * @return	state of the connection (CLOSED or -1 if not found)
     */
    public int getConnectionState(int dstLink, int dstPort, int srcLink, int srcPort) {
    	// Search through all connection states
    	for(Integer connectionState : map.keySet())
    		// Search through all Connections in the list
    		for(Connection c : map.get(connectionState))
    			// Return the connection state if the connection is found
    			if(c.srcLink == srcLink && c.srcPort == srcPort && c.dstLink == dstLink && c.dstPort == dstPort)
    				return connectionState;
    	
    	// Connection doesn't exist. Conclude as closed
    	return Connection.CLOSED;
    }
    
    /**
     * Find a port's waiting connection if exists. Returns null if not found.
     * 
     * @param	srcLink	source address
     * @param	srcPort soruce port
     * @return	corresponding connection (null if not found)
     */
    public Connection findWaitingConnection(int srcLink, int srcPort) {
		// Check if there are any SYN_RCVD (or waiting) Connections
    	if(map.get(Connection.SYN_RCVD) != null && !map.get(Connection.SYN_RCVD).isEmpty()) {
    		// Find a connection that matches srcLink and srcPort
    		for(Connection c : map.get(Connection.SYN_RCVD)) {
				if(c.srcLink == srcLink && c.srcPort == srcPort) {
					// Remove the connection from the list
					LinkedList<Connection> connections = map.remove(Connection.SYN_RCVD);
					connections.remove(c);
					
					// Reinsert the list to the map
					map.put(Connection.SYN_RCVD, connections);
					return c;
				}
			}
    	}
    	
    	// Connection doesn't exist. Return null
		return null;
	}
	
	private HashMap<Integer, LinkedList<Connection>> map;
}