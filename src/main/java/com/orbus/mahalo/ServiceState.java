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

/**
 * ServiceState defines the possible states for services and hosts registered with Mahalo mDNS / DNS-SD.
 *
 * @author Jeff Ward
 * @remarks Portions possibly created by Werner Randelshofer or Rick Blair
 */
public enum ServiceState
{
	ANNOUNCED("Announced", null),
	ANNOUNCING_2("Announcing 2", ANNOUNCED),
	ANNOUNCING_1("Announcing 1", ANNOUNCING_2),
	PROBING_3("Probing 3", ANNOUNCING_1),
	PROBING_2("Probing 2", PROBING_3),
	PROBING_1("Probing 1", PROBING_2);
	
    private final String _sName;
    private final ServiceState _eNextState;

    private ServiceState(String asName, ServiceState aeNextState)
    {
        _sName = asName;
        _eNextState = aeNextState;
    }

    public final String toString()
    {
        return _sName;
    }

    /**
     * Returns the next advanced state.
     * In general, this advances one step in the following sequence: PROBING_1,
     * PROBING_2, PROBING_3, ANNOUNCING_1, ANNOUNCING_2, ANNOUNCED.
     * Does not advance for ANNOUNCED and CANCELED state.
     */
    public final ServiceState advance()
    {
        return _eNextState;
    }

    /**
     * Returns to the next reverted state.
     * All states except CANCELED revert to PROBING_1.
     * Status CANCELED does not revert.
     */
    public final ServiceState revert()
    {
        return PROBING_1;
    }

    /**
     * Returns true, if this is a probing state.
     */
    public boolean isProbing()
    {
        return this == PROBING_1 || this == PROBING_2 || this == PROBING_3;
    }

    /**
     * Returns true, if this is an announcing state.
     */
    public boolean isAnnouncing()
    {
        return this == ANNOUNCING_1 || this == ANNOUNCING_2;
    }

    /**
     * Returns true, if this is an announced state.
     */
    public boolean isAnnounced()
    {
        return this == ANNOUNCED;
    }
}