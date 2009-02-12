/*
 * Copyright 2007-2008 Medsea Business Solutions S.L.
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

/**
 *
 * This exception is thrown by methods that fail while checking a file to determine the mime type.
 * @author Steven McArdle
 * @deprecated Use {@link eu.medsea.mimeutil.MimeException} instead!
 *
 */
public class MimeException extends RuntimeException {

	private static final long serialVersionUID = -1931354615779382666L;

	public MimeException(String message) {
		super(message);
	}

	public MimeException(Throwable t) {
		super(t);
	}

	public MimeException(String message, Throwable t) {
		super(message, t);
	}
}