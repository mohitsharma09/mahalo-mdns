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

import java.util.EventObject;

/**
 * ServiceEvent.
 *
 * @author Werner Randelshofer, Rick Blair
 * @version %I%, %G%
 */
public class ServiceEvent extends EventObject
{
    private static final long serialVersionUID = 1;
    
    private ServiceInfo _SrvInfo;
    private RemoteHostInfo _HostInfo;

    public ServiceEvent(Object source, ServiceInfo aSrvInfo, RemoteHostInfo aHostInfo)
    {
        super(source);
        _SrvInfo = aSrvInfo;
        _HostInfo = aHostInfo;
    }

    public ServiceInfo getSrvInfo() {
        return _SrvInfo;
    }

    public RemoteHostInfo getHostInfo(){
        return _HostInfo;
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("<" + getClass().getName() + "> ");
        buf.append(super.toString());
        buf.append(" ServiceInfo ");
        buf.append(getSrvInfo());
        buf.append(" host ");
        buf.append(getHostInfo());
        return buf.toString();
    }
}
