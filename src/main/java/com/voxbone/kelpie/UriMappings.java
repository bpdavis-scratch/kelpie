/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.voxbone.kelpie;


import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.jabberstudio.jso.JID;

/**
 * Mapping of jabber ids to telephone numbers, usefull if you want to also want communicaton
 * from SIP -> XMPP direction
 *
 */
public class UriMappings
{
	private static List<Mapping> mappings = new ArrayList<Mapping>();
	private static Logger log = Logger.getLogger(UriMappings.class);
	private static String host;
	private static String fakeId;
	private static boolean mapJID = false;
	private static boolean fwdJID = false;
	private static String fwdDOMAIN;


	
	private static class Mapping
	{
		public String sip_id;
		public JID jid;
		public String voiceResource;

		public Mapping(String sip_id, JID jid)
		{
			this.sip_id = sip_id;
			this.jid = jid;
			this.voiceResource = null;
		}
	}
	
	public static void configure(Properties properties) {
		fakeId = properties.getProperty("com.voxbone.kelpie.service_name", "kelpie");
		mapJID = Boolean.parseBoolean(properties.getProperty("com.voxbone.kelpie.map.strict", "false"));
		fwdJID = Boolean.parseBoolean(properties.getProperty("com.voxbone.kelpie.map.forward", "false"));
		fwdDOMAIN = properties.getProperty("com.voxbone.kelpie.map.forward.domain", "gmail.com");

		buildMap(properties);
	}
	
	public static void initialize()
	{
		for (Mapping m : mappings)
		{
			if (m.voiceResource == null) {
			Session sess = SessionManager.findCreateSession(host, m.jid);		
			sess.sendSubscribeRequest(new JID(fakeId + "@" + host), m.jid, "subscribe");
			}
		}
	}

	public static void buildMap(Properties p)
	{
		host = p.getProperty("com.voxbone.kelpie.hostname");
		for (Object okey : p.keySet())
		{
			String key = (String) okey;
			if (key.startsWith("com.voxbone.kelpie.mapping") && fwdJID == false)
			{
				String sip_id = key.substring("com.voxbone.kelpie.mapping.".length());				
				JID jid = new JID((String) p.get(key));
				log.debug("Adding " + sip_id + " => " + jid);
				mappings.add(new Mapping(sip_id, jid));
			}
		}
	}

	public static JID toJID(String sip_id)
	{
		for (Mapping m : mappings)
		{
			if (m.sip_id.equals(sip_id))
			{
				return m.jid;
			}
		}
		
		if (sip_id != null && !sip_id.startsWith("+") && (fwdJID && fwdDOMAIN != null) )
		{
			// full domain forwarder mode
			String [] fields = sip_id.split("\\+", 2);
			JID jid = new JID(fields[0] + "@" + fwdDOMAIN );
			log.debug("Adding forwarder " + sip_id + " => " + jid );
			mappings.add(new Mapping(sip_id, jid));
			
			for (Mapping m : mappings)
			{
				if (m.jid == jid)
				{
				Session sess = SessionManager.findCreateSession(host, m.jid);		
				try {
				sess.sendSubscribeRequest(new JID(fakeId + "@" + host), m.jid, "subscribe");
				} catch (Exception e) {
				log.debug("Error " + e + " subscribing to " + sip_id + " => " + jid);
				}
					int count = 0;
					while (count < 5 && m.voiceResource == null) 
					  try {
					    	log.debug("NULL resource for " + jid + " - sending probe...");
							sess.sendSubscribeRequest(new JID(fakeId + "@" + host), m.jid, "probe");
							count++;
							Thread.sleep(500);
                                        	} catch (Exception ex) { continue; }

						if (m.voiceResource == null && mapJID) {
							log.debug("Removing forwarder " + sip_id + " => " + jid );
							mappings.remove(m);
							SessionManager.removeSession(SessionManager.getSession(jid));
							return null;
						}

					  }
			}

		return jid;
			
		}
		else if (sip_id.contains("+") && !sip_id.startsWith("+") )
		{
				String [] fields = sip_id.split("\\+", 2);
				JID jid = new JID(fields[0] + "@" + fields[1]);
				log.debug("Resolving resources for " + jid );
				if (UriMappings.isDup(sip_id))
			 	{
					log.debug("Existing Mapping found for " + sip_id );
				} else {
					log.debug("Adding live " + sip_id + " => " + jid );
					mappings.add(new Mapping(sip_id, jid));
				}
				for (Mapping m : mappings)
				{
					if (m.jid == jid)
					{
					Session sess = SessionManager.findCreateSession(host, m.jid);		
					try {
					sess.sendSubscribeRequest(new JID(fakeId + "@" + host), m.jid, "subscribe");
					} catch (Exception e) {
					log.debug("Error " + e + " subscribing to " + sip_id + " => " + jid);
					}
						int count = 0;
						while (count < 5 && m.voiceResource == null) 
						    try {
							log.debug("NULL resource for " + jid);
							count++;
							Thread.sleep(500);
	                                        	} catch (Exception ex) { continue; }

						if (m.voiceResource == null && mapJID) {
							log.debug("Removing live " + sip_id + " => " + jid );
							mappings.remove(m);
							SessionManager.removeSession(SessionManager.getSession(jid));
							return null;
						}

					}
				}

			return jid;
		}

		return null;
	}
	
	public static String toSipId(JID jid)
	{
		for (Mapping m : mappings)
		{
			if (m.jid.match(jid))
			{
				return m.sip_id;
			}
		}
		return jid.getNode() + "+" + jid.getDomain();
	}
	
	public static void addVoiceResource(JID jid)
	{
		for (Mapping m : mappings)
		{
			if (m.jid.match(jid))
			{
				m.voiceResource = jid.getResource();
				log.info("Resource for " + m.jid + " set to " + jid.getResource());
			}
		}
	}
	
	public static String getVoiceResource(JID jid)
	{
		for (Mapping m : mappings)
		{
			if (m.jid.match(jid))
			{
				return m.voiceResource;
			}
		}
		return null;
	}

	public static boolean isDup(String sip_id){
               for (Mapping m : mappings)
               {
			if (m.sip_id.equals(sip_id))
			{
				return true;
			}
               }
               return false;
       }
	

}
