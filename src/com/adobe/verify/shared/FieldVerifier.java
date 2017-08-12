package com.adobe.verify.shared;

/**
 * <p>
 * FieldVerifier validates that the spreadsheet url the user enters is valid.
 * </p>
 */
public class FieldVerifier {

	/**
	 * Verifies that the specified url is valid
	 * 
	 * @param spreadsheet url
	 * @return true if valid, false if invalid
	 */
	public static boolean isValidURL(String name) {
		if (name == null) {
			return false;
		}
		String urlPattern = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
	    return name.matches(urlPattern);
	}
}
