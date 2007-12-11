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

import java.net.InetAddress;

import com.orbus.mahalo.dns.DNSRecord;

public class RemoteHostInfo {
	private InetAddress _Address;
	private String _sName;
	
	public RemoteHostInfo(DNSRecord.Address aAddressRecord) {
		_Address = aAddressRecord.getAddress();
		_sName = aAddressRecord.getName();
	}
	
	public InetAddress getAddress() {
		return _Address;
	}
	
	public String getName() {
		return _sName;
	}
}
