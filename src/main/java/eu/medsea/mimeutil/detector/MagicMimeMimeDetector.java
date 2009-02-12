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
package eu.medsea.mimeutil.detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.medsea.mimeutil.MimeException;
import eu.medsea.mimeutil.MimeUtil;

/**
 * The magic mime rules files are loaded in the following way.
 * <ol>
 * <li>From a JVM system property <code>magic-mime</code> i.e
 * <code>-Dmagic-mime=../my/magic/mime/rules</code></li>
 * <li>From any file named <code>magic.mime</code> that can be found on the
 * classpath</li>
 * <li>From a file named <code>.magic.mime</code> in the users home directory</li>
 * <li>From the normal Unix locations <code>/usr/share/file/magic.mime</code>
 * and <code>/etc/magic.mime</code> (in that order)</li>
 * <li>From the internal <code>magic.mime</code> file
 * <code>eu.medsea.mimeutil.magic.mime</code> if, and only if, no files are located
 * in step 4 above.</li>
 * </ol>
 * Each rule file is appended to the end of the existing rules so the earlier in
 * the sequence you define a rule means this will take precedence over rules
 * loaded later.
 * </p>
 * <p>
 * You can add new mime mapping rules using the
 * syntax defined for the Unix magic.mime file by placing these rules in any of
 * the files or locations listed above. You can also change an existing mapping
 * rule by redefining the existing rule in one of the files listed above. This
 * is handy for some of the more sketchy rules defined in the existing Unix
 * magic.mime files.
 * </p>
 * <p>
 * We use the <code>application/directory</code> mime type to identify
 * directories. Even though this is not an official mime type it seems to be
 * well accepted on the net as an unofficial mime type so we thought it was OK
 * for us to use as well.
 * </p>
 * <p>
 * This class is auto loaded by MimeUtil as it has an entry in the file called MimeDetectors.
 * MimeUtil reads this file at startup and calls Class.forName() on each entry found. This mean
 * the MimeDetector must have a no arg constructor.
 * </p>
 *
 * @author Steven McArdle.
 *
 */
public class MagicMimeMimeDetector extends MimeDetector {

	private static Log log = LogFactory.getLog(MagicMimeMimeDetector.class);

	private static String [] defaultLocations = {
		"/usr/share/mimelnk/magic",
		"/usr/share/file/magic.mime",
		"/etc/magic.mime" };
	private static List magicMimeFileLocations = Arrays.asList(defaultLocations);

	private static ArrayList mMagicMimeEntries = new ArrayList();

	// Initialise this MimeDetector and automatically register it with MimeUtil
	static {
		initMagicRules();
		MimeUtil.addMimeDetector(new MagicMimeMimeDetector());
	}

	public MagicMimeMimeDetector() {}

	public String getDescription() {
		return "Get the mime types of files or streams using the Unix file(5) magic.mime files";
	}

	/**
	 * Get the mime type of the data in the specified {@link InputStream}.
	 * Therefore, the <code>InputStream</code> must support mark and reset (see
	 * {@link InputStream#markSupported()}). If it does not support mark and
	 * reset, an {@link MimeException} is thrown.
	 *
	 * @param in
	 *            the stream from which to read the data.
	 * @return the mime types.
	 * @throws MimeException
	 *             if the specified <code>InputStream</code> does not support
	 *             mark and reset (see {@link InputStream#markSupported()}).
	 */
	public Collection getMimeTypesInputStream(final InputStream in) throws MimeException {
		Collection mimeTypes = new HashSet();
		int len = mMagicMimeEntries.size();
		try {
			for (int i = 0; i < len; i++) {
				MagicMimeEntry me = (MagicMimeEntry) mMagicMimeEntries.get(i);
				MagicMimeEntry matchingMagicMimeEntry = me.getMatch(in);
				if (matchingMagicMimeEntry != null) {
					mimeTypes.add(matchingMagicMimeEntry.getMimeType());
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return mimeTypes;
	}

	/**
	 * Try to get the mime types of a file using the <code>magic.mime</code> rules
	 * files.
	 *
	 * @param file
	 *            is a {@link File} object that points to a file or directory.
	 * @return the mime types.
	 * @throws MimeException
	 *             if the file cannot be parsed.
	 */
	public Collection getMimeTypesFile(final File file) throws MimeException {
		Collection mimeTypes = new HashSet();
		if (!file.exists()) {
			return mimeTypes;
		}
		if (file.isDirectory()) {
			mimeTypes.add(MimeUtil.DIRECTORY_MIME_TYPE);
			return mimeTypes;
		}

		int len = mMagicMimeEntries.size();
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
			for (int i = 0; i < len; i++) {
				MagicMimeEntry me = (MagicMimeEntry) mMagicMimeEntries.get(i);
				MagicMimeEntry matchingMagicMimeEntry = me.getMatch(raf);
				if (matchingMagicMimeEntry != null) {
					mimeTypes.add(matchingMagicMimeEntry.getMimeType());
				}
			}
		} catch (Exception e) {
			throw new MimeException("Error parsing file ["
					+ file.getAbsolutePath() + "]", e);
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (Exception e) {
					log.error("Error closing file.", e);
				}
			}
		}
		return mimeTypes;
	}


	/*
	 * This loads the magic.mime file rules into the internal parse tree in the
	 * following order 1. From any magic.mime that can be located on the
	 * classpath 2. From any magic.mime file that can be located using the
	 * environment variable MAGIC 3. From any magic.mime located in the users
	 * home directory ~/.magic.mime file if the MAGIC environment variable is
	 * not set 4. From the locations defined in the magicMimeFileLocations and
	 * the order defined 5. From the internally defined magic.mime file ONLY if
	 * we are unable to locate any of the files in steps 2 - 5 above Thanks go
	 * to Simon Pepping for his bug report
	 */
	private static void initMagicRules() {
		InputStream is = null;

		// Try to locate a magic.mime file locate by system property magic-mime
		try {
			String fname = System.getProperty("magic-mime");
			if (fname != null && fname.length() != 0) {
				is = new FileInputStream(fname);
				try {
					if (is != null) {
						parse("-Dmagic-mime=" + fname,
								new InputStreamReader(is));
					}
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (Exception e) {
							// ignore, but log in debug mode
							if (log.isDebugEnabled())
								log.debug(e.getMessage(), e);
						}
						is = null;
					}
				}
			}
		} catch (Exception e) {
			log
					.error(
							"Failed to parse custom magic mime file defined by system property -Dmagic-mime ["
									+ System.getProperty("magic-mime")
									+ "]. File will be ignored.", e);
		}

		// Try to locate a magic.mime file on the classpath
		try {
			is = MimeUtil.class.getClassLoader().getResourceAsStream(
					"magic.mime");
			try {
				if (is != null) {
					parse("classpath:magic-mime", new InputStreamReader(is));
				}
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {
						// ignore, but log in debug mode
						if (log.isDebugEnabled())
							log.debug(e.getMessage(), e);
					}
					is = null;
				}
			}
		} catch (Exception e) {
			log
					.error(
							"Failed to parse magic.mime rule file on the classpath. File will be ignored.",
							e);
		}

		// Now lets see if we have one in the users home directory. This is
		// named .magic.mime as opposed to magic.mime
		try {
			File f = new File(System.getProperty("user.home") + File.separator
					+ ".magic.mime");
			if (f.exists()) {
				is = new FileInputStream(f);
				try {
					if (is != null) {
						parse(f.getAbsolutePath(), new InputStreamReader(is));
					}
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (Exception e) {
							// ignore, but log in debug mode
							if (log.isDebugEnabled())
								log.debug(e.getMessage(), e);
						}
						is = null;
					}
				}

			}
		} catch (Exception e) {
			log
					.error(
							"Failed to parse .magic.mime file from the users home directory. File will be ignored.",
							e);
		}
		// Now lets see if we have an environment variable names MAGIC set. This
		// would normally point to a magic or magic.mgc file.
		// As we don't use these file types we will look to see if there is also
		// a magic.mime file at this location for us to use.
		try {
			String name = System.getProperty("MAGIC");
			if (name != null && name.length() != 0) {
				// Strip the .mgc from the end if it's there and add the .mime
				// extension
				if (name.indexOf('.') < 0) {
					name = name + ".mime";
				} else {
					// remove the mgc extension
					name = name.substring(0, name.indexOf('.') - 1) + "mime";
				}
				File f = new File(name);
				if (f.exists()) {
					is = new FileInputStream(f);
					try {
						if (is != null) {
							parse(f.getAbsolutePath(),
									new InputStreamReader(is));
						}
					} finally {
						if (is != null) {
							try {
								is.close();
							} catch (Exception e) {
								// ignore, but log in debug mode
								if (log.isDebugEnabled())
									log.debug(e.getMessage(), e);
							}
							is = null;
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(
				"Failed to parse magic.mime file from directory located by environment variable MAGIC. File will be ignored.", e);
		}

		// Parse the UNIX magic(5) magic.mime files. Since there can be
		// multiple, we have to load all of them.
		// We save, how many entries we have now, in order to fall back to our
		// default magic.mime that we ship,
		// if no entries were read from the OS.

		int mMagicMimeEntriesSizeBeforeReadingOS = mMagicMimeEntries.size();
		Iterator it = magicMimeFileLocations.iterator();
		while(it.hasNext()) {
			parseMagicMimeFileLocation((String)it.next());
		}

		if (mMagicMimeEntriesSizeBeforeReadingOS == mMagicMimeEntries.size()) {
			// Use the magic.mime that we ship
			try {
				String resource = "eu/medsea/mimeutil/magic.mime";
				is = MimeUtil.class.getClassLoader().getResourceAsStream(
						resource);
				parse("resource:" + resource, new InputStreamReader(is));
			} catch (Exception e) {
				log.error("Failed to process internal magic.mime file.", e);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {
						// ignore, but log in debug mode
						if (log.isDebugEnabled())
							log.debug(e.getMessage(), e);
					}
					is = null;
				}
			}
		}
	}

	private static void parseMagicMimeFileLocation(final String location) {
		InputStream is = null;

		List magicMimeFiles = getMagicFilesFromMagicMimeFileLocation(location);

		for (Iterator itFile = magicMimeFiles.iterator(); itFile.hasNext();) {
			File f = (File) itFile.next();
			try {
				is = new FileInputStream(f);
				if (f.exists()) {
					parse(f.getAbsolutePath(), new InputStreamReader(is));
				}
			} catch (Exception e) {
				log.warn(e.getMessage());
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {
						// ignore, but log in debug mode
						if (log.isDebugEnabled())
							log.debug(e.getMessage(), e);
					}
					is = null;
				}
			}
		}
	}

	private static List getMagicFilesFromMagicMimeFileLocation(
			final String magicMimeFileLocation) {
		List magicMimeFiles = new LinkedList();
		if (magicMimeFileLocation.indexOf('*') < 0) {
			magicMimeFiles.add(new File(magicMimeFileLocation));
		} else {
			int lastSlashPos = magicMimeFileLocation.lastIndexOf('/');
			File dir;
			String fileNameSimplePattern;
			if (lastSlashPos < 0) {
				dir = new File("someProbablyNotExistingFile").getAbsoluteFile()
						.getParentFile();
				fileNameSimplePattern = magicMimeFileLocation;
			} else {
				String dirName = magicMimeFileLocation.substring(0,
						lastSlashPos);
				if (dirName.indexOf('*') >= 0)
					throw new UnsupportedOperationException(
							"The wildcard '*' is not allowed in directory part of the location! Do you want to implement expressions like /path/**/*.mime for recursive search? Please do!");

				dir = new File(dirName);
				fileNameSimplePattern = magicMimeFileLocation
						.substring(lastSlashPos + 1);
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

	// Parse the magic.mime file
	private static void parse(final String magicFile, final Reader r) throws IOException {
		long start = System.currentTimeMillis();

		BufferedReader br = new BufferedReader(r);
		String line;
		ArrayList sequence = new ArrayList();

		long lineNumber = 0;
		line = br.readLine();
		if (line != null)
			++lineNumber;
		while (true) {
			if (line == null) {
				break;
			}
			line = line.trim();
			if (line.length() == 0 || line.charAt(0) == '#') {
				line = br.readLine();
				if (line != null)
					++lineNumber;
				continue;
			}
			sequence.add(line);

			// read the following lines until a line does not begin with '>' or
			// EOF
			while (true) {
				line = br.readLine();
				if (line != null)
					++lineNumber;
				if (line == null) {
					addEntry(magicFile, lineNumber, sequence);
					sequence.clear();
					break;
				}
				line = line.trim();
				if (line.length() == 0 || line.charAt(0) == '#') {
					continue;
				}
				if (line.charAt(0) != '>') {
					addEntry(magicFile, lineNumber, sequence);
					sequence.clear();
					break;
				}
				sequence.add(line);
			}

		}
		if (!sequence.isEmpty()) {
			addEntry(magicFile, lineNumber, sequence);
		}

		if (log.isDebugEnabled())
			log.debug("Parsing \"" + magicFile + "\" took "
					+ (System.currentTimeMillis() - start) + " msec.");
	}

	private static void addEntry(final String magicFile, final long lineNumber,
			final ArrayList aStringArray) {
		try {
			MagicMimeEntry magicEntry = new MagicMimeEntry(aStringArray);
			mMagicMimeEntries.add(magicEntry);
			// Add this to the list of known mime types as well
			if (magicEntry.getMimeType() != null) {
				MimeUtil.addKnownMimeType(magicEntry.getMimeType());
			}
		} catch (InvalidMagicMimeEntryException e) {
			// Continue on but lets print an exception so people can see there
			// is a problem
			log.warn(e.getClass().getName() + ": " + e.getMessage()
					+ ": file \"" + magicFile + "\": before or at line "
					+ lineNumber, e);
		}
	}
}