/**
 * Copyright 2007 Jeff Ward
 * Portions may be pulled from JmDNS and are therefore 
 * the copyright of the JmDNS team
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orbus.mahalo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.orbus.mahalo.dns.DNSPacket;

public class MahaloSocket {
	private static final Logger s_Logger = Logger.getLogger(MahaloSocket.class);
	private static final int MDNS_PORT = 5353;
	
	private NetworkInterface _NetInterface;
	private InetAddress _BoundAddress;
	
	private InetAddress _MulticastGroup;
    private MulticastSocket _MulticastSocket;
    private boolean _bContinueRunning = false;
    private Thread _ListeningThread;
    
    private List<MahaloSocketListener> _Listeners = new LinkedList<MahaloSocketListener>();
    
    public MahaloSocket(InetAddress aAddress) throws IOException {
    	_BoundAddress = aAddress;
    	_NetInterface = NetworkInterface.getByInetAddress(aAddress);
    	if(_NetInterface == null) {
    		throw new SocketException("Could not find network interface associated with requested address: " + aAddress);
    	}
    	openMulticastSocket();
    }
    
    public InetAddress getBoundAddress() {
    	return _BoundAddress;
    }
    
    public void addListener(MahaloSocketListener aListener) {
    	synchronized(_Listeners) {
    		if(!_Listeners.contains(aListener))
    			_Listeners.add(aListener);
    	}
    }
    
    public void removeListener(MahaloSocketListener aListener) {
    	synchronized(_Listeners) {
    		_Listeners.remove(aListener);
    	}
    }
    
    public void send(DNSPacket aMessage) {
    	send(aMessage, null, null);
    }
    
    public void send(DNSPacket aMessage, InetAddress aAddress, Integer iPort) {
    	if(aAddress == null)
    		aAddress = _MulticastGroup;
    	if(iPort == null)
    		iPort = MDNS_PORT;
    		
    	s_Logger.trace("Sending packet to " + aAddress + ":" + iPort + "\n" + aMessage);
    	
    	try
    	{
	    	ByteBuffer[] buffers = aMessage.write();
	    	for(ByteBuffer buffer : buffers)
	    	{
	    		buffer.flip();
	    		byte[] data = new byte[buffer.remaining()];
	    		buffer.get(data);
	    		DatagramPacket packet = new DatagramPacket(data, buffer.position(), aAddress, iPort);
	    		_MulticastSocket.send(packet);
	    	}
    	} catch(IOException e) {
    		s_Logger.warn("Error attempting to send DNSPacket: " + e.getMessage());
    		s_Logger.warn("Trying to recover...");
    		try {
    			recover();
    		} catch(IOException ioException) {
    			s_Logger.fatal("Error trying to recover!  Exception follows.");
        		s_Logger.fatal(ioException);
    		}
    	}
    }
    
    public synchronized void startListening() {
    	if(_ListeningThread == null) {
    		_bContinueRunning = true;
    		_ListeningThread = new Thread(new SocketListener());
    		_ListeningThread.start();
    	}
    }
    
    public synchronized void close() {
    	if (_bContinueRunning)
        {
    		s_Logger.info("Shutting down Mahalo socket connection");
//    		 Stop the listening thread
        	_bContinueRunning = false;
        	try {
        		_ListeningThread.join();
        	} catch(InterruptedException e) { }
        	_ListeningThread = null;
        	closeMulticastSocket();
        }
    }
    
    private void recover() throws IOException {
		closeMulticastSocket();
		openMulticastSocket();
    }
    
    private synchronized void closeMulticastSocket() {
    	if (_MulticastSocket != null) {
            // close socket
        	try { 
	        	_MulticastSocket.leaveGroup(_MulticastGroup);
	        	_MulticastSocket.close();
        	} catch(IOException e) {
        		s_Logger.warn("Error closing multicast socket", e);
        	}
        }
        
        _MulticastSocket = null;
        _MulticastGroup = null;
    }
    
    private synchronized void openMulticastSocket() throws IOException {
    	if(_MulticastSocket != null)
    		closeMulticastSocket();
    	
    	try {
    		_MulticastGroup = InetAddress.getByName(DNSPacket.MDNS_GROUP);
    		_MulticastSocket = new MulticastSocket(DNSPacket.MDNS_PORT);
    		_MulticastSocket.setNetworkInterface(_NetInterface);
            _MulticastSocket.setSoTimeout(1000);
            _MulticastSocket.setTimeToLive(255);
            _MulticastSocket.joinGroup(_MulticastGroup);
            
            _bContinueRunning = true;
    	} catch(IOException e) {
    		s_Logger.error("Error opening multicast socket.  Closing... (ignore any warnings about errors closing the socket)", e);
    		closeMulticastSocket();
    		throw e;
    	}
    }
    
    private boolean shouldIgnorePacket(DatagramPacket packet) {
    	boolean result = false;
        
        InetAddress from = packet.getAddress();
        if (from != null)
        {
            if (from.isLinkLocalAddress() && (!_BoundAddress.isLinkLocalAddress()))
            {
                // Ignore linklocal packets on regular interfaces, unless this is
                // also a linklocal interface. This is to avoid duplicates. This is
                // a terrible hack caused by the lack of an API to get the address
                // of the interface on which the packet was received.
                result = true;
            }
            if (from.isLoopbackAddress() && (!_BoundAddress.isLoopbackAddress()))
            {
                // Ignore loopback packets on a regular interface unless this is
                // also a loopback interface.
                result = true;
            }
        }
        
        return result;
    }
    
    private void onQuery(DNSPacket aPacket, InetAddress aAddress, int aiPort) {
    	s_Logger.trace("Informing listeners of received query: " + aPacket);
    	synchronized(_Listeners) {
	    	for(MahaloSocketListener listener : _Listeners) {
	    		listener.handleQuery(aPacket, aAddress, aiPort);
	    	}
    	}
    }
    
    private void onResponse(DNSPacket aPacket) {
    	s_Logger.trace("Informing listeners of received response: " + aPacket);
    	synchronized(_Listeners) {
	    	for(MahaloSocketListener listener : _Listeners) {
	    		listener.handleResponse(aPacket);
	    	}
    	}
    }
    
    private class SocketListener implements Runnable {
	    public void run() 
	    {
	    	byte buf[] = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
	        DatagramPacket packet = new DatagramPacket(buf, buf.length);
	        packet.setLength(buf.length);
	        
	        while (_bContinueRunning)
	        {
	        	try
	        	{
	                synchronized(this) {
	                	_MulticastSocket.receive(packet);
	                }
	        	
	                if (!shouldIgnorePacket(packet)) {
	                	ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
	                    DNSPacket dnsMessage = DNSPacket.Parse(buffer);
		
	                    if (dnsMessage.isQuery())
	                        onQuery(dnsMessage, packet.getAddress(), packet.getPort());
	                    else
	                        onResponse(dnsMessage);
	                }
	        	} catch(SocketTimeoutException e) { 
	        		// Ignore, this is just to keep us looping checking for cancelations
	        	} catch(IOException e) {
	        		s_Logger.warn("Error attempting to recieve DNSPacket: " + e.getMessage());
	        		s_Logger.warn("Trying to recover...");
	        		try {
	        			recover();
	        		} catch(IOException ioException) {
	        			s_Logger.fatal("Error trying to recover!  Exception follows.");
	            		s_Logger.fatal(ioException);
	            		break;
	        		}
	        	}
	        }
	        _bContinueRunning = false;
	        _ListeningThread = null;
	    }
    }
}
