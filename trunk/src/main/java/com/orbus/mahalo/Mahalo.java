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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.apache.log4j.Logger;

import com.orbus.mahalo.dns.DNSCache;

/**
 * mDNS implementation in Java.
 *
 * @version %I%, %G%
 * @author Jeff Ward
 * Note: Protions possibly authored by the JmDNS team:
 * Arthur van Hoff, Rick Blair, Jeff Sonstein,
 * Werner Randelshofer, Pierre Frisch, Scott Lewis
 * 
 * TODO: Need cache reaping to result in ServiceRemoved messages
 * TODO: Need cache reaping to warn about results that are about to expire
 * TODO: Share cache between Broadcaster and Browser
 * TODO: Need to include listening for service types (in JmDNS, but removed in Mahalo)
 * TODO: Need documentation for all classes
 */
public class Mahalo
{
	private static final Logger s_Logger = Logger.getLogger(Mahalo.class);
	private static final int REAP_INTERVAL = 10000;
	
	/**
     * The version of Mahalo mDNS / DNS-SD.
     */
    public static String VERSION = "0.5";

    private MahaloSocket _MahaloSocket;
    private MahaloBroadcaster _Broadcaster;
    private MahaloBrowser _Browser;
    private DNSCache _Cache = new DNSCache(100);  
    
    /**
     * Create an instance of Mahalo mDNS / DNS-SD.
     */
    public Mahalo() throws UnknownHostException, IOException {
    	this(null, null);
    }

    /**
     * Create an instance of Mahalo mDNS / DNS-SD and bind it to a
     * specific network interface given its IP-address.
     */
    public Mahalo(InetAddress aAddress) throws UnknownHostException, IOException {
    	this(aAddress, null);
    }
       
    public Mahalo(InetAddress aAddress, String asName)
    	throws UnknownHostException, IOException
    {
    	if(aAddress == null) {
    		// Get local host.
    		// TODO: On null, we should bind to ALL interfaces and give each interface its own name.
    		aAddress = InetAddress.getLocalHost();
    	}
    	
    	// If this address is the loopback, don't allow that.
    	if(aAddress.isLoopbackAddress())
    	{
    		// We can't report loopback to other servers, so it doesn't makes sense.
    		aAddress = null;
    		
    		// Query for a resonable replacement that we can report by enumerating all the network interfaces
    		for(Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces(); nics.hasMoreElements() && aAddress == null; ) {
    			NetworkInterface nic = nics.nextElement();
    			for(Enumeration<InetAddress> addresses = nic.getInetAddresses(); addresses.hasMoreElements() && aAddress == null; ) {
    				InetAddress address = addresses.nextElement();
    				// TODO: IPv6 support
    				if(address instanceof Inet4Address && !address.isLoopbackAddress()) {
    					s_Logger.debug("After searching, we are broadcasting on address " + address);
    					aAddress = address;
    				}
    			}
    		}
    		
    		// TODO: Query for *all* replacements for multihomed machines.
    	}
    	
    	if(asName == null)
    	{
    		asName = aAddress.getHostName();
    		if(aAddress.toString().contains(asName)) {
    			// This is just an IP address, it needs to go.  This happens on unix machines
    			// where the address and the host name are not necessarily bound together.
    			// In this case, use localhosts's name instead
    			asName = InetAddress.getLocalHost().getHostName();    			
    		}
    		s_Logger.debug("Name not provided.  Using host name from address: " + asName);
    	}
    	
    	s_Logger.debug("Creating Mahalo socket at address " + aAddress);
    	_MahaloSocket = new MahaloSocket(aAddress);
    	_MahaloSocket.startListening();
    	    		
    	s_Logger.debug("Creating broadcaster and browser with host name " + asName);
    	_Broadcaster = new MahaloBroadcaster(_MahaloSocket, asName);
    	_Browser = new MahaloBrowser(_MahaloSocket, _Cache);
    }
    
    public void start() {
    	s_Logger.info("Starting Mahalo mDNS / DNS-SD");
    	_Broadcaster.start();
    }

    /**
     * Listen for services of a given type. The type has to be a fully qualified
     * type name such as <code>_http._tcp.local.</code>.
     *
     * @param type     full qualified service type, such as <code>_http._tcp.local.</code>.
     * @param listener listener for service updates
     */
    public void addServiceListener(String asType, ServiceListener aListener)
    {
        _Browser.addServiceListener(asType, aListener);
    }

    /**
     * Remove listener for services of a given type.
     *
     * @param listener listener for service updates
     */
    public void removeServiceListener(String asType, ServiceListener aListener)
    {
    	_Browser.addServiceListener(asType, aListener);
    }

    /**
     * Register a service. The service is registered for access by other jmdns clients.
     * The name of the service may be changed to make it unique.
     */
    public void registerService(ServiceInfo aInfo) throws IOException {
    	_Broadcaster.registerService(aInfo);
    }

    /**
     * Unregister a service. The service should have been registered.
     */
    public void unregisterService(ServiceInfo aInfo) {
       _Broadcaster.unregisterService(aInfo);
    }

    /**
     * Unregister all services.
     */
    public void unregisterAllServices() {
        _Broadcaster.unregisterAllServices();
    }
    
    
    
    /**
     * Close down jmdns. Release all resources and unregister all services.
     */
    public void close() {
    	s_Logger.info("Closing Mahalo mDNS / DNS-DS");
        _Broadcaster.stop();
        _MahaloSocket.close();
    }

    /**
     * Register a service type. If this service type was not already known,
     * all service listeners will be notified of the new service type. Service types
     * are automatically registered as they are discovered.
     */
//    public void registerServiceType(String type)
//    {
//        String name = type.toLowerCase();
//        if (serviceTypes.get(name) == null)
//        {
//            if ((type.indexOf("._mdns._udp.") < 0) && !type.endsWith(".in-addr.arpa."))
//            {
//                Collection list;
//                synchronized (this)
//                {
//                    serviceTypes.put(name, type);
//                    list = new LinkedList(typeListeners);
//                }
//                for (Iterator iterator = list.iterator(); iterator.hasNext();)
//                {
//                    ((ServiceTypeListener) iterator.next()).serviceTypeAdded(new ServiceEvent(this, type, null, null));
//                }
//            }
//        }
//    }
}