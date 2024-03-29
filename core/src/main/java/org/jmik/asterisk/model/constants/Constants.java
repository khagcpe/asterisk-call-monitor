package org.jmik.asterisk.model.constants;

import java.util.ResourceBundle;

/**
 * See default properties in callmonitor.properties.
 * 
 * @author Michele La Porta
 *
 */
public class Constants {

	static final String CONFIGURATION_BUNDLE = "callmonitor";

	private static ResourceBundle resourceBundle;

	static{
		resourceBundle = ResourceBundle.getBundle(CONFIGURATION_BUNDLE);
	}

	public static String asteriskIpAddress = resourceBundle.getString("callmonitor.asterisk.ip");
	public static int asteriskPort = Integer.parseInt(resourceBundle.getString("callmonitor.asterisk.port"));

	public static String asteriskManagerUser = resourceBundle.getString("callmonitor.asterisk.manager.user");
	public static String asteriskManagerPassword = resourceBundle.getString("callmonitor.asterisk.manager.password");

	public static String conferenceMonitorIpAddress = resourceBundle.getString("callmonitor.conference.ip");
	public static int conferenceMonitorPort = Integer.parseInt(resourceBundle.getString("callmonitor.conference.port"));
	

}
