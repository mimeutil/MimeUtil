/*
 * Copyright 2007-2009 Medsea Business Solutions S.L.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.medsea.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * The <code>MimeUtil</code> utility class is used to detect mime types from either a files extension
 * or by looking into the file at various offsets and comparing or looking for certain values in the same way
 * as the Unix <code>file(1)</code> command.
 * </p>
 * <p>
 * It is important to note that mime matching is not an exact science meaning that a positive match does not guarantee the returned mime type
 * is correct. It is only a best guess method of matching and should be used with this in mind.
 * </p>
 * <p>
 * Both the file extension mapping and the magic mime number rules can be extended by the user.
 * </p>
 * <p>
 * The extension mime mappings are loaded in the following way.
 * <ol>
 * <li>Load the properties file from the mime utility jar named <code>eu.medsea.mime.mime-types.properties</code>.</li>
 * <li>Locate and load a file named <code>.mime-types.properties</code> from the users home directory if one exists.</li>
 * <li>Locate and load a file named <code>mime-types.properties</code> from the classpath if one exists</li>
 * <li>locate and load a file named by the JVM property <code>mime-mappings</code> i.e. <code>-Dmime-mappings=../my-mime-types.properties</code></li>
 * </ol>
 * Each property file loaded will add to the list of extensions. If there is a clash of extension names then the last one loaded wins,
 * this makes it possible to completely change the mime types associated to a file extension declared in previous property files.
 * </p>
 * <p>
 * We acquired many mappings from many different sources on the net for the extension mappings. The internal list is quite large and there can
 * be many associated mime types. These may not match what you are expecting so you can add the mapping you want to change to your own property
 * file following the rules above. If you provide a mapping for an extension then any previously loaded mappings will be removed and only the
 * mappings you define will be returned. This can be used to map certain extensions that are incorrectly returned for our environment defined
 * in the internal property file.
 * </p>
 * <p>
 * If we have not provided a mapping for a file extension that you know the mime type for you can add this to your custom property file
 * so that a correct mime type is returned for you.
 * <p>
 * The magic mime rules files are loaded in the following way.
 * <ol>
 * <li>From a JVM system property <code>magic-mime</code> i.e <code>-Dmagic-mime=../my/magic/mime/rules</code></li>
 * <li>From any file named <code>magic.mime</code> that can be found on the classpath</li>
 * <li>From a file named <code>.magic.mime</code> in the users home directory</li>
 * <li>From the normal Unix locations <code>/usr/share/file/magic.mime</code> and <code>/etc/magic.mime</code> (in that order)</li>
 * <li>From the internal <code>magic.mime</code> file <code>eu.medsea.mime.magic.mime</code> if, and only if, no files are located in step 4 above.</li>
 * </ol>
 * Each rule file is appended to the end of the existing rules so the earlier in the sequence you define a rule means this will take precedence
 * over rules loaded later.
 * </p>
 * <p>
 * As with the extension mappings you can add new mime mapping rules using the syntax defined for the Unix magic.mime file by placing these rules
 * in any of the files or locations listed above. You can also change an existing mapping rule by redefining the existing rule in one of the files
 * listed above. This is handy for some of the more sketchy rules defined in the existing Unix magic.mime files.
 * </p>
 * <p>
 * When using the utility methods we always try to return a mime type even if no mapping can be found. In the case of no mapping we have defined
 * the utility to return <code>application/octet-stream</code> by default. This can be overridden and you can have a no-match return any mime type you
 * define, even unofficial mime types that you made up just for your application such as <code>application/x-unknown-mime-type</code>. This allows
 * your application to provide special handling on a no-match such as executing a custom business process.
 * </p>
 * <p>
 * We use the <code>application/directory</code> mime type to identify directories. Even though this is not an official mime type
 * it seems to be well accepted on the net as an unofficial mime type so we thought it was OK for us to use as well.
 * </p>
 * @author Steven McArdle.
 *
 */
public class MimeUtil
{
	private static Log log = LogFactory.getLog(MimeUtil.class);

	private static Pattern mimeSplitter = Pattern.compile ("[/;]++" );

	private static final String[] magicMimeFileLocations = {
		"/usr/share/mimelnk/magic",
		"/usr/share/file/magic.mime",
		"/etc/magic.mime"
	};

	/**
	 * Mime type used to identify no match
	 */
	public static String UNKNOWN_MIME_TYPE="application/octet-stream";

	/**
	 * Mime type used to identify a directory
	 */
	public static final String DIRECTORY_MIME_TYPE="application/directory";



	// the native byte order of the underlying OS. "BIG" or "little" Endian
	private static ByteOrder nativeByteOrder = ByteOrder.nativeOrder();

	// Extension MimeTypes
	private static Map extMimeTypes;

	// All mime types know to the utility
	private static Map mimeTypes = new HashMap();

    private static ArrayList mMagicMimeEntries = new ArrayList();

 	/**
	 * Get the native byte order of the OS on which you are running. It will be either big or little endian.
	 * This is used internally for the magic mime rules mapping.
	 *
	 * @return ByteOrder
	 */
	public static ByteOrder getNativeOrder() {
		return MimeUtil.nativeByteOrder;
	}

	/**
	 * Get the mime type of the data in the specified {@link InputStream}. Therefore,
	 * the <code>InputStream</code> must support mark and reset
	 * (see {@link InputStream#markSupported()}). If it does not support mark and reset,
	 * an {@link MimeException} is thrown.
	 *
	 * @param in the stream from which to read the data.
	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
	 * @throws MimeException if the specified <code>InputStream</code> does not support mark and reset (see {@link InputStream#markSupported()}).
	 */
	public static String getMimeType(InputStream in)
		throws MimeException
	{
		if (!in.markSupported())
			throw new MimeException("InputStream does not support mark and reset!");

		String mimeType = null;
		int len = mMagicMimeEntries.size();
		try {
			List matchingEntries = new LinkedList();
			for (int i=0; i < len; i++) {
				MagicMimeEntry me = (MagicMimeEntry) mMagicMimeEntries.get(i);
				MatchingMagicMimeEntry matchingMagicMimeEntry = me.getMatch(in);
	            if (matchingMagicMimeEntry != null) {
	            	matchingEntries.add(matchingMagicMimeEntry);
	            }
			}

	        MatchingMagicMimeEntry mostSpecificMatchingEntry = getMostSpecificMatchingEntry(matchingEntries);
	        if (mostSpecificMatchingEntry != null)
	        	return mostSpecificMatchingEntry.getMimeType();

	        String textPlain = checkForTextPlain(in);
	        if (textPlain != null)
	        	return textPlain;

		} catch(Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			if(mimeType == null) {
				mimeType = UNKNOWN_MIME_TYPE;
			}
		}

		return mimeType;
	}

    /**
     * Get the mime type of a file using a path which can be relative to the JVM
     * or an absolute path. The path can point to a file or directory location and if
     * the path does not point to an actual file or directory the {@link #UNKNOWN_MIME_TYPE}is returned.
     * <p>
     * Their is an exception to this and that is if the <code>fname</code> parameter does NOT point to a real file or directory
     * and extFirst is <code>true</code> then a match against the file extension could be found and would be returned.
     * </p>
     * @param fname points to a file or directory
     * @param extFirst if <code>true</code> will first use file extension mapping and then then <code>magic.mime</code> rules.
     * If <code>false</code> it will try to match the other way around i.e. <code>magic.mime</code> rules and then file extension.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
     * @throws MimeException if while using the <code>magic.mime</code> rules there is a problem processing the file.
     */
    public static String getMimeType(String fname, boolean extFirst) throws MimeException {
    	return getMimeType(new File(fname), extFirst);
    }

    /**
     * This is a convenience method where the order of lookup is set to extension mapping first.
     * @see #getMimeType(String fname, boolean extFirst)
     */
    public static String getMimeType(String fname) throws MimeException {
    	return getMimeType(fname, true);
    }

    /**
     * Get the mime type of a file using a <code>File</code> object which can be relative to the JVM
     * or an absolute path. The path can point to a file or directory location and if
     * the path does not point to an actual file or directory the {@link #UNKNOWN_MIME_TYPE}is returned.
     * <p>
     * Their is an exception to this and that is if the <code>file</code> parameter does NOT point to a real file or directory
     * and extFirst is <code>true</code> then a match against the file extension could be found and would be returned.
     * </p>
     * @param file points to a file or directory
     * @param extFirst if <code>true</code> will first use file extension mapping and then then <code>magic.mime</code> rules.
     * If <code>false</code> it will try to match the other way around i.e. <code>magic.mime</code> rules and then file extension.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
     * @throws MimeException if while using the <code>magic.mime</code> rules there is a problem processing the file.
     */
	public static String getMimeType(File file, boolean extFirst) throws MimeException {
		String mimeType = null;
		// Try getting by extension
		if(extFirst) {
			mimeType = getExtensionMimeTypes(file);
			if(UNKNOWN_MIME_TYPE.equals(mimeType)) {
				return getMagicMimeType(file);
			}
			return mimeType;
		} else {
			mimeType = getMagicMimeType(file);
			if(UNKNOWN_MIME_TYPE.equals(mimeType)){
				return getExtensionMimeTypes(file);
			}
			return mimeType;
		}
	}

    /**
     * This is a convenience method where the order of lookup is set to extension mapping first.
     * @see #getMimeType(File f, boolean extFirst)
     */
	public static String getMimeType(File f) throws MimeException {
		return getMimeType(f, true);
	}


	/**
	 * Gives you the best match for your requirements.
	 * <p>
	 * You can pass the accept header from a browser request to this method along with a comma separated
	 * list of possible mime types returned from say getExtensionMimeTypes(...) and the best match according
	 * to the accept header will be returned.
	 * </p>
	 * <p>
	 * The following is typical of what may be specified in an HTTP Accept header:
	 * </p>
	 * <p>
	 * Accept: text/xml, application/xml, application/xhtml+xml, text/html;q=0.9, text/plain;q=0.8, video/x-mng, image/png, image/jpeg, image/gif;q=0.2, text/css, *&#47;*;q=0.1
	 * </p>
	 * <p>
	 * The quality parameter (q) indicates how well the user agent handles the MIME type. A value of 1 indicates the MIME type is understood perfectly,
	 * and a value of 0 indicates the MIME type isn't understood at all.
	 * </p>
	 * <p>
	 * The reason the image/gif MIME type contains a quality parameter of 0.2, is to indicate that PNG & JPEG are preferred over GIF if the server is using
	 * content negotiation to deliver either a PNG or a GIF to user agents. Similarly, the text/html quality parameter has been lowered a little, to ensure
	 * that the XML MIME types are given in preference if content negotiation is being used to serve an XHTML document.
	 * </p>
	 * @param accept is a comma separated list of mime types you can accept including QoS parameters. Can pass the Accept: header directly.
	 * @param canProvide is a comma separated list of mime types that can be provided such as that returned from a call to getExtensionMimeTypes(...)
	 * @return the best matching mime type possible.
	 */
	public static String getPreferedMimeType(String accept, String canProvide) {
		if(canProvide == null || canProvide.trim().length() == 0) {
			throw new MimeException("Must specify at least one mime type that can be provided.");
		}
		if(accept == null || accept.trim().length() == 0) {
			accept = "*/*";
		}

		// If an accept header is passed in then lets remove the Accept part
		if(accept.indexOf(":") > 0) {
			accept = accept.substring(accept.indexOf(":")+1);
		}

		// Remove any unwanted spaces from the wanted mime types for instance text/html; q=0.4
		accept = accept.replaceAll(" ", "");

		return getBestMatch(accept, getList(canProvide));
	}

	// Check each entry in each of the wanted lists against the entries in the can provide list.
	// We take into consideration the QoS indicator
	private static String getBestMatch(String accept, List canProvideList) {

		if(canProvideList.size() == 1) {
			// If we only have one mime type that can be provided then thats what we provide even if
			// the wanted list does not contain this entry or it's the worst QoS.
			// This will cover the majority of cases
			return (String)canProvideList.get(0);
		}

		Map wantedMap = normaliseWantedMap(accept, canProvideList);

		String bestMatch  = null;
		double qos = 0.0;
		Iterator it = wantedMap.keySet().iterator();
		while(it.hasNext()) {
			List wantedList = (List)wantedMap.get(it.next());
			Iterator it2 = wantedList.iterator();
			while(it2.hasNext()) {
				String mimeType = (String)it2.next();
				double q = getMimeQuality(mimeType);
				String majorComponent = getMajorComponent(mimeType);
				String minorComponent = getMinorComponent(mimeType);
				if(q > qos) {
					qos = q;
					bestMatch = majorComponent + "/" + minorComponent;
				}
			}
		}
		// Gone through all the wanted list and found the best match possible
		return bestMatch;
	}

	// Turn a comma separated string into a list
	private static List getList(String options) {
		List list = new ArrayList();
		String [] array = options.split(",");
		for(int i = 0; i < array.length; i++) {
			list.add(array[i].trim());
		}
		return list;
	}

	// Turn a comma separated string of accepted mime types into a Map
	// based on the list of mime types that can be provided
	private static Map normaliseWantedMap(String accept, List canProvide) {
		Map map = new LinkedHashMap();
		String [] array = accept.split(",");

		for(int i = 0; i < array.length; i++) {
			String mimeType = array[i].trim();
			String major = getMajorComponent(mimeType);
			String minor = getMinorComponent(mimeType);
			double qos = getMimeQuality(mimeType);

			if(major.contains("*")) {
				// All canProvide types are acceptable with the qos defined OR 0.01 if not defined
				Iterator it = canProvide.iterator();
				while(it.hasNext()) {
					String mt = (String)it.next();
					List list = (List)map.get(MimeUtil.getMajorComponent(mt));
					if(list == null) {
						list = new ArrayList();
					}
					list.add(mt + ";q=" + qos);
					map.put(MimeUtil.getMajorComponent(mt), list);
				}
			} else if(minor.contains("*")) {
				Iterator it = canProvide.iterator();
				while(it.hasNext()) {
					String mt = (String)it.next();
					if(getMajorComponent(mt).equals(major)) {
						List list = (List)map.get(major);
						if(list == null) {
							list = new ArrayList();
						}
						list.add(major + "/" + getMinorComponent(mt) + ";q=" + qos);
						map.put(major,list);
					}
				}

			} else {
				if(canProvide.contains(major + "/" + minor)) {
					List list = (List)map.get(major);
					if(list == null) {
						list = new ArrayList();
					}
					list.add(major + "/" + minor + ";q=" + qos);
					map.put(major,list);
				}
			}
		}
		return map;
	}

    /**
    *
    * Utility method to get the quality part of a mime type.
    * If it does not exist then it is always set to q=1.0 unless
    * it's a wild card.
    * For the major component wild card the value is set to 0.01
    * For the minor component wild card the value is set to 0.02
    * <p>
    * Thanks to the Apache organisation or these settings.
    *
    * @param mimeType a valid mime type string with or without a valid q parameter
    * @return the quality value of the mime type either calculated from the rules above or the actual value defined.
    * @throws MimeException this is thrown if the mime type pattern is invalid.
    */
   public static double getMimeQuality(String mimeType) throws MimeException{
   	if(mimeType == null) {
   		throw new MimeException("Invalid MimeType [" + mimeType + "].");
   	}
   	String [] parts = mimeSplitter.split(mimeType);
   	if(parts.length < 2) {
   		throw new MimeException("Invalid MimeType [" + mimeType + "].");
   	}
   	if(parts.length > 2) {
   		for(int i = 2; i < parts.length; i++) {
   			if(parts[i].trim().startsWith("q=")) {
   				// Get the number part
   				try {
   					// Get the quality factor
   					double d = Double.parseDouble(parts[i].split("=")[1].trim());
   					return d > 1.0 ? 1.0 : d;
   				}catch(NumberFormatException e) {
   					throw new MimeException("Invalid Mime quality indicator [" + parts[i].trim() + "]. Must be a valid double between 0 and 1");
   				}catch(Exception e) {
   					throw new MimeException("Error parsing Mime quality indicator.", e);
   				}
   			}
   		}
   	}
		// No quality indicator so always assume its 1 unless a wild card is used
		if(parts[0].contains("*")) {
			return 0.01;
		} else if(parts[1].contains("*")) {
			return 0.02;
		} else {
			// Assume q value of 1
			return 1.0;
		}
   }

	/*
	 * This loads the mime-types.properties files that define mime types based on file extensions using the following load sequence
	 * 1. Loads the property file from the mime utility jar named eu.medsea.mime.mime-types.properties.
	 * 2. Locates and loads a file named .mime-types.properties from the users home directory if one exists.
	 * 3. Locates and loads a file named mime-types.properties from the classpath if one exists
	 * 4. locates and loads a file named by the JVM property mime-mappings i.e. -Dmime-mappings=../my-mime-types.properties
	 */
	private static void initMimeTypes() {
		extMimeTypes = new Properties();
		// Load the file extension mappings from the internal property file and then
		// from the custom property files if they can be found
		try {
			// Load the default supplied mime types
			((Properties)extMimeTypes).load(MimeUtil.class.getClassLoader().getResourceAsStream("eu/medsea/util/mime-types.properties"));

			// Load any .mime-types.properties from the users home directory
			try {
				File f = new File(System.getProperty("user.home") + File.separator + ".mime-types.properties");
				if(f.exists()) {
					InputStream is = new FileInputStream(f);
					if(is != null) {
						log.debug("Found a custom .mime-types.properties file in the users home directory.");
						Properties props = new Properties();
						props.load(is);
						if(props.size() > 0) {
							extMimeTypes.putAll(props);
						}
						log.debug("Successfully parsed .mime-types.properties from users home directory.");
					}
				}
			}catch(Exception e) {
				log.error("Failed to parse .magic.mime file from users home directory. File will be ignored.", e);
			}

			try {
				// Load any classpath provided mime types that either extend or override the default mime type entries
				InputStream is =  MimeUtil.class.getClassLoader().getResourceAsStream("mime-types.properties");
				if(is != null) {
					log.debug("Found a custom mime-types.properties file on the classpath.");
					Properties props = new Properties();
					props.load(is);
					if(props.size() > 0) {
						extMimeTypes.putAll(props);
					}
					log.debug("Successfully loaded custome mime-type.properties file from classpath.");
				}
			}catch(Exception e) {
				log.error("Failed to load the mime-types.properties file located on the classpath. File will be ignored.");
			}
			try {
				// Load any mime extension mappings file defined with the JVM property -Dmime-mappings=../my/custom/mappings.properties
				String fname = System.getProperty("mime-mappings");
				if(fname != null && fname.length() != 0) {
					InputStream is = new FileInputStream(fname);
					if(is != null) {
						if(log.isDebugEnabled()) {
							log.debug("Found a custom mime-mappings property defined by the property -Dmime-mappings [" + System.getProperty("mime-mappings") + "].");
						}
						Properties props = new Properties();
						props.load(is);
						if(props.size() > 0) {
							extMimeTypes.putAll(props);
						}
						log.debug("Successfully loaded the mime mappings file from property -Dmime-mappings [" + System.getProperty("mime-mappings") + "].");
					}
				}
			}catch(Exception e) {
				log.error("Failed to load the mime-mappings file defined by the property -Dmime-mappings [" + System.getProperty("mime-mappings") + "].");
			}
		} catch (Exception e) {
			// log the error but don't throw the exception up the stack
			log.error("Error loading internal mime-types.properties", e);
		}finally {
			// Load the mime types into the known mime types map
			Iterator it = extMimeTypes.values().iterator();
			while(it.hasNext()) {
				String [] types = ((String)it.next()).split(",");
				for(int i = 0; i < types.length; i++) {
					addKnownMimeType(types[i]);
				}
			}
		}
	}


	/*
	 * This loads the magic.mime file rules into the internal parse tree in the following order
	 * 1. From any magic.mime that can be located on the classpath
	 * 2. From any magic.mime file that can be located using the environment variable MAGIC
	 * 3. From any magic.mime located in the users home directory ~/.magic.mime file if the MAGIC environment variable is not set
	 * 4. From the locations defined in the magicMimeFileLocations and the order defined
	 * 5. From the internally defined magic.mime file ONLY if we are unable to locate any of the files in steps 2 - 5 above
	 * Thanks go to Simon Pepping for his bug report
	 */
	private static void initMagicRules()
	{
		InputStream is = null;

		// Try to locate a magic.mime file locate by system property magic-mime
		try {
			String fname=System.getProperty("magic-mime");
			if(fname != null && fname.length() != 0) {
				is = new FileInputStream(fname);
				try {
					if(is != null) {
						parse("-Dmagic-mime="+fname, new InputStreamReader(is));
					}
				} finally {
					if(is != null) {
						try {
							is.close();
						}catch(Exception e) {
							// ignore, but log in debug mode
							if (log.isDebugEnabled())
								log.debug(e.getMessage(), e);
						}
						is = null;
					}
				}
			}
		}catch(Exception e) {
			log.error("Failed to parse custom magic mime file defined by system property -Dmagic-mime [" + System.getProperty("magic-mime") + "]. File will be ignored.", e);
		}

		// Try to locate a magic.mime file on the classpath
		try {
			is = MimeUtil.class.getClassLoader().getResourceAsStream("magic.mime");
			try {
				if(is != null) {
					parse("classpath:magic-mime", new InputStreamReader(is));
				}
			} finally {
				if(is != null) {
					try {
						is.close();
					}catch(Exception e) {
						// ignore, but log in debug mode
						if (log.isDebugEnabled())
							log.debug(e.getMessage(), e);
					}
					is = null;
				}
			}
		}catch(Exception e) {
			log.error("Failed to parse magic.mime rule file on the classpath. File will be ignored.", e);
		}

		// Now lets see if we have one in the users home directory. This is named .magic.mime as opposed to magic.mime
		try {
			File f = new File(System.getProperty("user.home") + File.separator + ".magic.mime");
			if(f.exists()) {
				is = new FileInputStream(f);
				try {
					if(is != null) {
						parse(f.getAbsolutePath(), new InputStreamReader(is));
					}
				} finally {
					if(is != null) {
						try {
							is.close();
						}catch(Exception e) {
							// ignore, but log in debug mode
							if (log.isDebugEnabled())
								log.debug(e.getMessage(), e);
						}
						is = null;
					}
				}

			}
		}catch(Exception e) {
			log.error("Failed to parse .magic.mime file from the users home directory. File will be ignored.", e);
		}
		// Now lets see if we have an environment variable names MAGIC set. This would normally point to a magic or magic.mgc file.
		// As we don't use these file types we will look to see if there is also a magic.mime file at this location for us to use.
		try {
			String name = System.getProperty("MAGIC");
			if(name != null && name.length() != 0) {
				// Strip the .mgc from the end if it's there and add the .mime extension
				if(name.indexOf('.') < 0) {
					name = name + ".mime";
				} else {
					// remove the mgc extension
					name = name.substring(0, name.indexOf('.')-1) + "mime";
				}
				File f = new File(name);
				if(f.exists()){
					is = new FileInputStream(f);
					try {
						if(is != null) {
							parse(f.getAbsolutePath(), new InputStreamReader(is));
						}
					} finally {
						if(is != null) {
							try {
								is.close();
							}catch(Exception e) {
								// ignore, but log in debug mode
								if (log.isDebugEnabled())
									log.debug(e.getMessage(), e);
							}
							is = null;
						}
					}
				}
			}
		}catch(Exception e) {
			log.error("Failed to parse magic.mime file from directory located by environment variable MAGIC. File will be ignored.", e);
		}


		// Parse the UNIX magic(5) magic.mime files. Since there can be multiple, we have to load all of them.
		// We save, how many entries we have now, in order to fall back to our default magic.mime that we ship,
		// if no entries were read from the OS.
		int mMagicMimeEntriesSizeBeforeReadingOS = mMagicMimeEntries.size();
		for(int i = 0; i< magicMimeFileLocations.length; i++) {
			String magicMimeFileLocation = magicMimeFileLocations[i];
			List magicMimeFiles = getMagicFilesFromMagicMimeFileLocation(magicMimeFileLocation);

			for (Iterator itFile = magicMimeFiles.iterator(); itFile.hasNext(); ) {
				File f = (File) itFile.next();
				try {
					is = new FileInputStream(f);
					if(f.exists()) {
						parse(f.getAbsolutePath(), new InputStreamReader(is));
					}
				} catch(Exception e) {
					log.warn(e.getMessage());
				} finally {
					if(is != null) {
						try {
							is.close();
						}catch(Exception e) {
							// ignore, but log in debug mode
							if (log.isDebugEnabled())
								log.debug(e.getMessage(), e);
						}
						is = null;
					}
				}
			}
		}

		if (mMagicMimeEntriesSizeBeforeReadingOS == mMagicMimeEntries.size()) {
			// Use the magic.mime that we ship
			try {
				String resource = "eu/medsea/util/magic.mime";
				is = MimeUtil.class.getClassLoader().getResourceAsStream(resource);
				parse("resource:" + resource, new InputStreamReader(is));
			} catch(Exception e) {
				log.error("Failed to process internal magic.mime file.", e);
			} finally {
				if(is != null) {
					try {
						is.close();
					}catch(Exception e) {
						// ignore, but log in debug mode
						if (log.isDebugEnabled())
							log.debug(e.getMessage(), e);
					}
					is = null;
				}
			}
		}
	}

	private static List getMagicFilesFromMagicMimeFileLocation(String magicMimeFileLocation)
	{
		List magicMimeFiles = new LinkedList();
		if (magicMimeFileLocation.indexOf('*') < 0) {
			magicMimeFiles.add(new File(magicMimeFileLocation));
		}
		else {
			int lastSlashPos = magicMimeFileLocation.lastIndexOf('/');
			File dir;
			String fileNameSimplePattern;
			if (lastSlashPos < 0) {
				dir = new File("someProbablyNotExistingFile").getAbsoluteFile().getParentFile();
				fileNameSimplePattern = magicMimeFileLocation;
			}
			else {
				String dirName = magicMimeFileLocation.substring(0, lastSlashPos);
				if (dirName.indexOf('*') >= 0)
					throw new UnsupportedOperationException("The wildcard '*' is not allowed in directory part of the location! Do you want to implement expressions like /path/**/*.mime for recursive search? Please do!");

				dir = new File(dirName);
				fileNameSimplePattern = magicMimeFileLocation.substring(lastSlashPos + 1);
			}

			if (!dir.isDirectory())
				return Collections.EMPTY_LIST;

			String s = fileNameSimplePattern.replaceAll("\\.", "\\\\.");
			s = s.replaceAll("\\*", ".*");
			Pattern fileNamePattern = Pattern.compile(s);

			File[] files = dir.listFiles();
			for (int i = 0; i < files.length; i++) {
				File file = files[i];

				if (fileNamePattern.matcher(file.getName()).matches())
					magicMimeFiles.add(file);
			}
		}
		return magicMimeFiles;
	}

	static {
		initMimeTypes();
		initMagicRules();
	}

	// Parse the magic.mime file
    private static void parse(String magicFile, Reader r) throws IOException {
    	long start = System.currentTimeMillis();

        BufferedReader br = new BufferedReader(r);
        String line;
        ArrayList sequence = new ArrayList();

        long lineNumber = 0;
        line = br.readLine();
        if (line != null) ++lineNumber;
        while (true) {
             if (line == null) {
                break;
            }
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
            	line = br.readLine();
            	if (line != null) ++lineNumber;
                continue;
            }
            sequence.add(line);

            // read the following lines until a line does not begin with '>' or EOF
            while(true) {
	            line = br.readLine();
	            if (line != null) ++lineNumber;
	            if(line == null) {
	            	addEntry(magicFile, lineNumber, sequence);
	            	sequence.clear();
	            	break;
	            }
	            line = line.trim();
	            if (line.length() == 0 || line.charAt(0) == '#') {
	                continue;
	            }
	            if(line.charAt(0) != '>') {
	            	addEntry(magicFile, lineNumber, sequence);
	            	sequence.clear();
	            	break;
	            }
	            sequence.add(line);
            }

        }
        if(!sequence.isEmpty()) {
        	addEntry(magicFile, lineNumber, sequence);
        }

        if (log.isDebugEnabled())
        	log.debug("Parsing \"" + magicFile + "\" took " + (System.currentTimeMillis() - start) + " msec.");
    }

    private static void addEntry(String magicFile, long lineNumber, ArrayList aStringArray) {
        try {
			MagicMimeEntry magicEntry = new MagicMimeEntry(aStringArray);
            mMagicMimeEntries.add(magicEntry);
            // Add this to the list of known mime types as well
			if(magicEntry.getMimeType() != null) {
				addKnownMimeType(magicEntry.getMimeType());
			}
		} catch (InvalidMagicMimeEntryException e) {
			// Continue on but lets print an exception so people can see there is a problem
            log.warn(e.getClass().getName() + ": " + e.getMessage() + ": file \"" + magicFile + "\": before or at line " + lineNumber, e);
		}
    }

    private static MatchingMagicMimeEntry getMostSpecificMatchingEntry(List notExactlyMatchingEntries)
    {
    	MatchingMagicMimeEntry mostSpecificMatchingEntry = null;
        for (Iterator it = notExactlyMatchingEntries.iterator(); it.hasNext();) {
			MatchingMagicMimeEntry entry = (MatchingMagicMimeEntry) it.next();
			if (mostSpecificMatchingEntry == null)
				mostSpecificMatchingEntry = entry;
			else if (mostSpecificMatchingEntry.getSpecificity() < entry.getSpecificity())
				mostSpecificMatchingEntry = entry;
		}
        return mostSpecificMatchingEntry;
    }

    private static String checkForTextPlain(InputStream in)
    {
    	// read the first 1024 bytes of what may be a text file.
    	byte[] content = new byte[1024];

    	try {
    		in.mark(1024);
    		try {
    			int offset = 0;
    			while (true) {
    				int bytesToRead = content.length - offset;
    				if (bytesToRead < 1)
    					break;

    				int bytesRead = in.read(content, offset, bytesToRead);
    				if (bytesRead < 0)
    					break;

    				offset += bytesRead;
    			}
    			if (offset < content.length) {
    				byte[] tmp = new byte[offset];
    				System.arraycopy(content, 0, tmp, 0, tmp.length);
    				content = tmp;
    			}
    		} finally {
    			in.reset();
    		}
    	} catch (IOException e) {
    		log.warn(e.getClass().getName() + ": " + e.getMessage(), e);
    		return null;
    	}

    	return checkForTextPlain(content);
    }

    private static String checkForTextPlain(RandomAccessFile raf)
    {
    	// read the first 1024 bytes of what may be a text file.
    	byte[] content = new byte[1024];

    	try {
    		raf.seek(0);
			if (raf.length() < 1024)
				content = new byte[(int)raf.length()];
			else
				content = new byte[1024];

			raf.readFully(content);
		} catch (IOException e) {
			log.warn(e.getClass().getName() + ": " + e.getMessage(), e);
			return null;
		}

		return checkForTextPlain(content);
    }

    private static String checkForTextPlain(byte[] content)
    {
    	if (content.length == 0)
    		return "application/x-empty";

    	// TODO we should check for all valid encodings - or at least for UTF-8 - right now, we only check for ASCII

    	for (int i = 0; i < content.length; i++) {
			int b = content[i] & 0xff;
			if (b < 9)
				return null;

			if (b > 175)
				return null;
		}
    	return "text/plain";
    }

	/**
	 * Get the mime type of a file using the <code>magic.mime</code> rules files.
	 * @param file is a {@link File} object that points to a file or directory.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
	 * @throws MimeException if the file cannot be parsed.
	 */
    public static String getMagicMimeType(File file) throws MimeException {
    	if(!file.exists()) {
    		return UNKNOWN_MIME_TYPE;
    	}
    	if(file.isDirectory()) {
    		return DIRECTORY_MIME_TYPE;
    	}
        int len = mMagicMimeEntries.size();
        RandomAccessFile raf = null;
        try {
	        raf = new RandomAccessFile(file, "r");
	        List matchingEntries = new LinkedList();
	        for (int i=0; i < len; i++) {
	            MagicMimeEntry me = (MagicMimeEntry) mMagicMimeEntries.get(i);
	            MatchingMagicMimeEntry matchingMagicMimeEntry = me.getMatch(raf);
	            if (matchingMagicMimeEntry != null) {
	            	matchingEntries.add(matchingMagicMimeEntry);
	            }
	        }

	        MatchingMagicMimeEntry mostSpecificMatchingEntry = getMostSpecificMatchingEntry(matchingEntries);
	        if (mostSpecificMatchingEntry != null)
	        	return mostSpecificMatchingEntry.getMimeType();

	        String textPlain = checkForTextPlain(raf);
	        if (textPlain != null)
	        	return textPlain;
        } catch(Exception e) {
        	throw new MimeException("Error parsing file [" + file.getAbsolutePath() + "]", e);
        }finally {
        	if(raf != null) {
        		try {
        			raf.close();
        		}catch(Exception e) {
        			log.error("Error closing file.", e);
        		}
        	}
        }
        return UNKNOWN_MIME_TYPE;
    }

    /**
     * Utility method to get the major part of a mime type
     * i.e. the bit before the '/' character
     *
     * @param mimeType you want to get the major part from
     * @return major component of the mime type
     * @throws MimeException if you pass in an invalid mime type structure
     */
    public static String getMajorComponent(String mimeType) throws MimeException {
    	if(mimeType == null) {
    		throw new MimeException("Invalid MimeType [" + mimeType + "].");
    	}
    	String [] parts = mimeSplitter.split(mimeType);
    	if(parts.length < 2) {
    		throw new MimeException("Invalid MimeType [" + mimeType + "].");
    	}
    	return parts[0].trim();
    }

    /**
     * Utility method to get the minor part of a mime type
     * i.e. the bit after the '/' character
     *
     * @param mimeType you want to get the minor part from
     * @return minor component of the mime type
     * @throws MimeException if you pass in an invalid mime type structure
     */
    public static String getMinorComponent(String mimeType) throws MimeException{
    	if(mimeType == null) {
    		throw new MimeException("Invalid MimeType [" + mimeType + "].");
    	}
    	String [] parts = mimeSplitter.split(mimeType);
    	if(parts.length < 2) {
    		throw new MimeException("Invalid MimeType [" + mimeType + "].");
    	}
    	return parts[1].trim();
    }

    /**
     * Get the extension part of a file name defined by the file parameter.
     * @param file a file object
     * @return the file extension or null if it does not have one.
     */
    public static String getFileExtension(File file) {
    	return MimeUtil.getFileExtension(file.getName());
    }

    /**
     * Get the extension part of a file name defined by the fname parameter.
     * There may be no extension or it could be a single part extension such as .bat
     * or a multi-part extension such as tar.gz
     * @param fileName a relative or absolute path to a file
     * @return the file extension or null if it does not have one.
     */
	public static String getFileExtension(String fileName) {
		// Remove any path element from this name
		File file = new File(fileName);
		fileName = file.getName();
		if (fileName == null || fileName.indexOf(".") < 0) {
			return "";
		}
		return fileName.substring(fileName.indexOf(".") + 1);

		/*
		String extension = fileName.substring(fileName.indexOf(".") + 1);
		// Could be that the path actually had a '.' in it so lets check
		if(extension.contains(File.separator) || extension.contains("/")) {
			extension = "";
		}
		return extension;
		*/
	}

	/**
	 * While all of the property files and magic.mime files are being loaded the utility keeps a list of mime types it's seen.
	 * You can add other mime types to this list using this method. You can then use the isMimeTypeKnown(...) utility method to see
	 * if a mime type you have matches one that the utility already understands.
	 * <p>
	 * For instance if you had a mime type of abc/xyz and passed this to isMimeTypeKnown(...) it would return false unless you specifically
	 * add this to the know mime types using this method.
	 * </p>
	 * @param mimeType a mime type you want to add to the known mime types. Duplicates are ignored.
	 * @see #isMimeTypeKnown(String mimetype)
	 */
	// Add a mime type to the list of known mime types.
	public static void addKnownMimeType(String mimeType) {
		try {
			String key = getMajorComponent(mimeType);
			Set s = (Set)mimeTypes.get(key);
			if(s == null) {
				s = new TreeSet();
			}
			s.add(getMinorComponent(mimeType));
			mimeTypes.put(key, s);
		}catch(MimeException ignore) {
			// A couple of entries in the magic mime file don't follow the rules so ignore them
		}
	}

	/**
	 * Check to see if this mime type is one of the types seen during initialisation
	 * or has been added at some later stage using addKnownMimeType(...)
	 * @param mimeType
	 * @return true if the mimeType is in the list else false is returned
	 * @see #addKnownMimeType(String mimetype)
	 */
	public static boolean isMimeTypeKnown(String mimeType) {
		try {
			Set s = (Set)mimeTypes.get(getMajorComponent(mimeType));
			if(s == null) {
				return false;
			}
			return s.contains(getMinorComponent(mimeType));
		}catch(MimeException e) {
			return false;
		}
	}

	/**
	 * Get the first in a comma separated list of mime types. Useful when using extension mapping
	 * that can return multiple mime types separate by commas and you only want the first one.
	 * Will return UNKNOWN_MIME_TYPE if the passed in list is null or empty.
	 *
	 * @param mimeTypes comma separated list of mime types
	 * @return the first in a comma separated list of mime types or the UNKNOWN_MIME_TYPE if the mimeTypes parameter is null or empty.
	 */
	public static String getFirstMimeType(String mimeTypes) {
		if(mimeTypes != null && mimeTypes.trim().length() != 0) {
			return mimeTypes.split(",")[0].trim();
		}
		return UNKNOWN_MIME_TYPE;
	}

	/**
	 * Get the mime type of a file using file extension mappings. The file path can be a relative or absolute or can be a completely
	 * non-existent file as only the extension is important.
	 * @param file is a <code>File</code> object that points to a file or directory. If the file or directory cannot be found
	 * {@link #UNKNOWN_MIME_TYPE} is returned.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
	 * @throws MimeException if the file cannot be parsed.
	 */
    public static String getExtensionMimeTypes(File file) {
    	return getExtensionMimeTypes(file.getName());
    }

	/**
	 * Get the mime type of a file using file extension mappings. The file path can be a relative or absolute or can be a completely
	 * non-existent file as only the extension is important.
	 * @param fname is a path that points to a file or directory. If the file or directory cannot be found
	 * {@link #UNKNOWN_MIME_TYPE} is returned.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
	 * @throws MimeException if the file cannot be parsed.
	 */
    public static String getExtensionMimeTypes(String fname) {
    	String fileExtension = getFileExtension(fname);
    	// First try case insensitive match
    	String mimeType = (String)extMimeTypes.get(fileExtension);
    	if(mimeType != null && mimeType.trim().length() != 0) {
    		return mimeType;
    	}
    	// Failed to find case insensitive extension so lets try again with lowercase
    	mimeType = (String)extMimeTypes.get(fileExtension.toLowerCase());
    	if(mimeType != null && mimeType.trim().length() != 0) {
    		return mimeType;
    	}
    	return UNKNOWN_MIME_TYPE;
    }

	/**
	 * Get the mime type of a file using the <code>magic.mime</code> rules files.
	 * @param fname is a path location to a file or directory.
 	 * @return the mime type. Never returns <code>null</code> (if the mime type cannot be found, {@link #UNKNOWN_MIME_TYPE} is returned).
	 * @throws MimeException if the file cannot be parsed.
	 */
	public static String getMagicMimeType(String fname) throws MimeException {
		return getMagicMimeType(new File(fname));
	}

    /**
     * The default mime type returned by a no match i.e. is not matched in either the extension mapping or magic.mime rules is
     * application/octet-stream. However, applications may want to treat a no match different from a match that could return application/octet-stream.
     * This method allows you to set a different mime type to represent a no match such as a custom mime type like application/unknown-mime-type
     * @param mimeType set the default returned mime type for a no match.
     */
    public static void setUnknownMimeType(String mimeType) {
    	UNKNOWN_MIME_TYPE = mimeType;
    }
}
