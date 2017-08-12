package com.adobe.verify.server;

import com.adobe.verify.client.GreetingService;
import com.adobe.verify.shared.FieldVerifier;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.google.gdata.client.batch.BatchInterruptedException;
//import com.google.gdata.client.authn.oauth.*;
import com.google.gdata.client.spreadsheet.*;
import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
//import com.google.gdata.data.*;
//import com.google.gdata.data.batch.*;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.model.atom.Feed;
import com.google.gdata.util.ServiceException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.SecurityUtils;
import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.GeocoderRequestBuilder;
import com.google.code.geocoder.model.GeocodeResponse;
import com.google.code.geocoder.model.GeocoderRequest;
import com.google.code.geocoder.model.GeocoderResult;
import com.google.code.geocoder.model.GeocoderStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.*;

/**
 * A basic struct to store cell row/column information and the associated RnCn
 * identifier.
 */
class CellAddress {
	public final int row;
	public final int col;
	public final String idString;

	/**
	 * Constructs a CellAddress representing the specified {@code row} and
	 * {@code col}.  The idString will be set in 'RnCn' notation.
	 */
	public CellAddress(int row, int col) {
		this.row = row;
		this.col = col;
		this.idString = String.format("R%sC%s", row, col);
	}
}


/**
 * The server-side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class GreetingServiceImpl extends RemoteServiceServlet implements GreetingService {

	private static String CLIENT_ID = "508006610328-rl7jrt5j7ih7ciil0pj65pc7m99mbt8e.apps.googleusercontent.com";
	private static String CLIENT_SECRET = "tIrvjw5yXxoaImx96DUnQiV0";
	private static String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
	private static String[] SCOPESArray = {"https://spreadsheets.google.com/feeds", "https://spreadsheets.google.com/feeds/spreadsheets/private/full", "https://docs.google.com/feeds"};
	private static final List<String> SCOPES = Arrays.asList(SCOPESArray);
	private static int MAX_ROWS = 0;
	private static int LAST_ROW = 0;
	private static final int MAX_COLS = 9;
	private static final String[] COLUMNS = new String[] { "Address", "City", "Country", "Postal_Code", "State_Province", "Country", "Google Verified Address", "Latitude", "Longitude" };
	private static final Set<String> COLUMNS_SET = new HashSet<String>(Arrays.asList(COLUMNS));

	public String greetServer(String input) 
			throws MalformedURLException, IOException, GeneralSecurityException, IllegalArgumentException, Exception {

		// Escape data from the client to avoid cross-site script vulnerabilities.
		input = escapeHtml(input);

		// Verify that the input is valid. 
		if (!FieldVerifier.isValidURL(input)) {
			// If the input is not valid, throw an IllegalArgumentException back to
			// the client.
			throw new IllegalArgumentException("Must be valid google spreadsheet url");
		}
		System.out.println(input);

		String key = "";
		if( input.contains("/d/") && input.split("/d/")[1].contains("/") ){
			key = input.split("/d/")[1].split("/")[0];
		}
		else {
			throw new IllegalArgumentException("Must be valid google spreadsheet url");
		}

		String applicationName = "AdobeAddressVerify";

		PrivateKey p12key = null;
		try {
			InputStream p12file = GreetingServiceImpl.class.getResourceAsStream("Adobe-Address-Verify-b060e80c82e2.p12");
			p12key = retrieveServiceAccountPrivateKeyFromP12File(p12file);
			p12file.close();
		} 
		catch(Exception e) {
			System.out.println(e);
		}

		HttpTransport httpTransport = new NetHttpTransport();
		JacksonFactory jsonFactory = new JacksonFactory();
		GoogleCredential credential = new GoogleCredential.Builder()
				.setTransport(httpTransport)
				.setJsonFactory(jsonFactory)
				.setServiceAccountId("adobe-p12@adobe-address-verify.iam.gserviceaccount.com")
				.setServiceAccountScopes(SCOPES)
				.setServiceAccountPrivateKey(p12key)
				.build();
		SpreadsheetService service = new SpreadsheetService(applicationName);
		service.setOAuth2Credentials(credential);

		URL SPREADSHEET_FEED_URL = new URL(
				"https://spreadsheets.google.com/feeds/spreadsheets/private/full");

		try {
			SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL,
					SpreadsheetFeed.class);
		} catch (ServiceException e3) {
			throw new Exception("Unable to reach google spreadsheet service");
		}

		URL url = FeedURLFactory.getDefault().getWorksheetFeedUrl(key, "private", "full");
		WorksheetFeed worksheetFeed = null;
		try {
			worksheetFeed = service.getFeed(url, WorksheetFeed.class);
		} catch (ServiceException e2) {
			throw new Exception("Unable to reach google spreadsheet service");
		}
		List<WorksheetEntry> worksheetList = worksheetFeed.getEntries();
		WorksheetEntry worksheet = worksheetList.get(0);

		//Fetch the list feed of the worksheet.
		URL listFeedUrl = worksheet.getListFeedUrl();
		ListFeed listFeed = null;
		try {
			listFeed = service.getFeed(listFeedUrl, ListFeed.class);
		} catch (ServiceException e2) {
			throw new Exception("Unable to reach google spreadsheet service");
		}
		MAX_ROWS = listFeed.getTotalResults();
		LAST_ROW = MAX_ROWS+1;
		if (MAX_ROWS <= 0) {
			throw new IllegalArgumentException("Google spreadsheet must have at least 1 address");
		}

		// Fetch the cell feed of the worksheet.
		URL cellFeedUrl = null;
		try {
			String cellUrl = worksheet.getCellFeedUrl().toString()+ "?min-row=1&max-row=" + LAST_ROW;
			cellFeedUrl = new URI(cellUrl).toURL();
		} catch (URISyntaxException e1) {
			e1.printStackTrace();
		}
		System.out.println(cellFeedUrl);
		CellFeed cellFeed = null;
		try {
			cellFeed = service.getFeed(cellFeedUrl, CellFeed.class);
		} catch (ServiceException e1) {
			throw new Exception("Unable to reach google spreadsheet service");
		}

		System.out.println("cellFeed done");

		// Batch updates only available from CellFeed, so using Cellfeed
		// Can be retrieved from ListFeed using column names if we don't need batching

		List<CellEntry> allCells = cellFeed.getEntries();
		System.out.println("allCells done");

		// Check Column Names
		for (CellEntry cell : allCells) {
			if ( 1 == cell.getCell().getRow() ) {
				if ( !COLUMNS_SET.contains(cell.getCell().getValue()) ) {
					throw new IllegalArgumentException("Unexpected columns in Google spreadsheet");
				}
			}
			else {
				break;
			}
		}
		System.out.println("column check done");

		HashMap<String, String> geocodedAddresses = new HashMap<String, String>();
		HashMap<String, String[]> geocodedAddressesCache = new HashMap<String, String[]>();
		HashMap<Integer, String> addresses = new HashMap<Integer, String>();

		// Iterate through cells
		Integer row_index = 1;
		String address = "";
		ArrayList<String> cells = new ArrayList<String>();
		for (CellEntry cell : allCells) {
			int rowNo = cell.getCell().getRow();
			if ( row_index != rowNo ) {
				// Store address of previous row
				if (!cells.isEmpty()) {
					address = cells.get(0)+", "+cells.get(1)+", "+cells.get(4)+" - "+cells.get(3)+", "+cells.get(5);
				}
				System.out.println(address+" row_index "+row_index);
				addresses.put(row_index, address);

				// Prepare for next row of address components
				row_index = rowNo;
				cells = new ArrayList<String>();
				cells.add(cell.getCell().getValue());
				address = "";
			}
			else {
				cells.add(cell.getCell().getValue());
			}
		}
		if (!cells.isEmpty()) {
			address = cells.get(0)+", "+cells.get(1)+", "+cells.get(4)+" - "+cells.get(3)+", "+cells.get(5);
			System.out.println(address+" row_index "+row_index);
			addresses.put(row_index, address);
		}

		System.out.println("addresses loaded into hashmap");
		for (int rowNo = 2; rowNo<=LAST_ROW; rowNo++) {
			String[] results = {"","",""};
			address = addresses.get(rowNo);
			if ( !address.isEmpty() ) {
				if (geocodedAddressesCache.containsKey(address)) {
					results = geocodedAddressesCache.get(address);
				}
				else {
					results = geocodeAddress(address);
					geocodedAddressesCache.put(address, results);
				}
			}

			System.out.println(rowNo+"-"+results[0]+results[1]+results[2]);
			geocodedAddresses.put("R"+rowNo+"C"+7, results[0]);
			geocodedAddresses.put("R"+rowNo+"C"+8, results[1]);
			geocodedAddresses.put("R"+rowNo+"C"+9, results[2]);

			try {
				Thread.sleep(110);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		System.out.println("Geocoding completed");

		CellFeed batchRequest = new CellFeed();

		for (int eachRow = 2; eachRow <= LAST_ROW; ++eachRow) {
			for (int eachCol = 7; eachCol <= MAX_COLS; ++eachCol) {
				CellEntry batchOperation = null;
				try {
					batchOperation = createUpdateOperation(eachRow, eachCol, geocodedAddresses.get("R"+eachRow+"C"+eachCol), worksheet.getCellFeedUrl(), service);
				} catch (ServiceException e) {
					throw new Exception("Unable to reach google spreadsheet service");
				}
				batchRequest.getEntries().add(batchOperation);
			}
		}

		// Get the batch feed URL and submit the batch request
		CellFeed batchfeed = null;
		try {
			batchfeed = service.getFeed(cellFeedUrl, CellFeed.class);
		} catch (ServiceException e) {
			throw new Exception("Unable to reach google spreadsheet service");
		}
		Link batchLink = batchfeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);
		URL batchUrl = new URL(batchLink.getHref());
		CellFeed batchResponse = null;
		int retries = 0;
		while (retries++<3) {
			try {
				batchResponse = service.batch(batchUrl, batchRequest);
				retries = 3;
			} catch (BatchInterruptedException e) {
				e.printStackTrace();
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}

		// Print any errors that may have happened.
		boolean isSuccess = true;
		for (CellEntry entry : batchResponse.getEntries()) {
			String batchId = BatchUtils.getBatchId(entry);
			if (!BatchUtils.isSuccess(entry)) {
				isSuccess = false;
				BatchStatus status = BatchUtils.getBatchStatus(entry);
				System.out.println("\n" + batchId + " failed (" + status.getReason()
				+ ") " + status.getContent());
			}
		}
		if (isSuccess) {
			System.out.println("Batch operations successful.");
			return "Success! Spreadsheet updated";
		}

		throw new Exception("Operation failed");

	}

	/**
	 * Create cell entry objects for batch update request
	 * - wait for 2 seconds logic in case we hit 10 requests per second google api limit
	 * @return cell entry object
	 */
	private CellEntry createUpdateOperation(int row, int col, String value, URL cellFeedUrl, SpreadsheetService service)
			throws ServiceException, IOException {
		String batchId = "R" + row + "C" + col;
		URL entryUrl = new URL(cellFeedUrl.toString() + "/" + batchId);
		CellEntry entry = service.getEntry(entryUrl, CellEntry.class);
		entry.changeInputValueLocal(value);
		BatchUtils.setBatchId(entry, batchId);
		BatchUtils.setBatchOperationType(entry, BatchOperationType.UPDATE);
		return entry;
	}

	/**
	 * Retrieve geocoded address
	 * - wait for 2 seconds logic in case we hit 10 requests per second google api limit
	 * @return [address, longitude, latitude]
	 * @throws IOException 
	 */
	private String[] geocodeAddress(String address) throws IOException, Exception{
		String[] geocodeResults = {"","",""};
		final Geocoder geocoder = new Geocoder();
		GeocoderRequest geocoderRequest = new GeocoderRequestBuilder().setAddress(address).setLanguage("en").getGeocoderRequest();
		Boolean errorFree = false;
		int retries = 0;
		while (!errorFree && retries++ <= 3) {
			GeocodeResponse geocoderResponse = null;
			geocoderResponse = geocoder.geocode(geocoderRequest);
			List<GeocoderResult> results = geocoderResponse.getResults();
			if (GeocoderStatus.OK == geocoderResponse.getStatus()) {
				errorFree = true;
				geocodeResults[0] = results.get(0).getFormattedAddress();
				geocodeResults[1] = results.get(0).getGeometry().getLocation().getLat().toString();
				geocodeResults[2] = results.get(0).getGeometry().getLocation().getLng().toString();
			}
			else if (GeocoderStatus.ZERO_RESULTS == geocoderResponse.getStatus()) {
				return geocodeResults;
			}
			else if (GeocoderStatus.OVER_QUERY_LIMIT == geocoderResponse.getStatus()) {
				if (retries<3) {
					try {
						// in case it is because of 10 requests per second limit
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				else {
					throw new Exception("Geocoder error - "+geocoderResponse.getStatus());
				}
			}
			else {
				throw new Exception("Geocoder error - "+geocoderResponse.getStatus());
			}
		}
		return geocodeResults;
	}

	/**
	 * Retrieve private key from p12 file
	 * 
	 * @return Spreadsheet with matching name
	 */
	private PrivateKey retrieveServiceAccountPrivateKeyFromP12File(InputStream p12File)
			throws GeneralSecurityException, IOException {
		PrivateKey serviceAccountPrivateKey = SecurityUtils.loadPrivateKeyFromKeyStore(
				SecurityUtils.getPkcs12KeyStore(), p12File, "notasecret",
				"privatekey", "notasecret");
		return serviceAccountPrivateKey;
	}

	/**
	 * Escape an html string. Escaping data received from the client helps to
	 * prevent cross-site script vulnerabilities.
	 * 
	 * @param html the html string to escape
	 * @return the escaped string
	 */
	private String escapeHtml(String html) {
		if (html == null) {
			return null;
		}
		return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}
}
