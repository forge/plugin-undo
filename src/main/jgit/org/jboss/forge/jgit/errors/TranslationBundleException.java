/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jboss.forge.jgit.errors;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.forge.jgit.errors.TranslationBundleException;

/**
 * Common base class for all translation bundle related exceptions.
 */
public abstract class TranslationBundleException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final Class bundleClass;
	private final Locale locale;

	/**
	 * To construct an instance of {@link TranslationBundleException}
	 *
	 * @param message
	 *            exception message
	 * @param bundleClass
	 *            bundle class for which the exception occurred
	 * @param locale
	 *            locale for which the exception occurred
	 * @param cause
	 *            original exception that caused this exception. Usually thrown
	 *            from the {@link ResourceBundle} class.
	 */
	protected TranslationBundleException(String message, Class bundleClass, Locale locale, Exception cause) {
		super(message, cause);
		this.bundleClass = bundleClass;
		this.locale = locale;
	}

	/**
	 * @return bundle class for which the exception occurred
	 */
	final public Class getBundleClass() {
		return bundleClass;
	}

	/**
	 * @return locale for which the exception occurred
	 */
	final public Locale getLocale() {
		return locale;
	}
}
